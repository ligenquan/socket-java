package com.sdu.network.jsocket.nio.server.reactor;

import com.sdu.network.jsocket.aio.bean.Message;
import com.sdu.network.jsocket.aio.bean.MessageAck;
import com.sdu.network.jsocket.nio.buf.JFrameBuffer;
import com.sdu.network.jsocket.utils.JSocketUtils;
import com.sdu.network.serializer.KryoSerializer;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 多线程服务模型:
 *
 *  1: QAcceptThread线程负责客户端的连接
 *
 *  2: QSelectorThread负责客户端数据通信
 *
 *  3: ExecutorService负责业务处理
 *
 * @author hanhan.zhang
 * */
public class JNioReactorServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JNioReactorServer.class);

    private AtomicBoolean stopped = new AtomicBoolean(true);

    private JNioAcceptThread acceptThread;

    private Set<JNioSelectorThread> selectorThreads = new HashSet<>();

    // 业务线程池
    private ExecutorService workExecutor;

    private long maxReadBufferBytes;

    private KryoSerializer serializer;

    @Setter
    @Getter
    @NoArgsConstructor
    public static class JServerArgs {
        // 绑定地址
        private String host;
        // 绑定端口
        private int port;

        // FrameBuffer缓冲区最大容量
        private int maxReadBufferBytes;

        private int selectorThreadNumber;

        private int queueSizePerSelectorThread;

        private int minWorkerThread;

        private int maxWorkerThread;

        private KryoSerializer serializer;
    }

    /**
     * 连接请求处理线程
     * */
    private class JNioAcceptThread extends Thread {
        // 绑定地址
        private String host;
        // 绑定端口
        private int port;

        private Selector acceptSelector;

        private JSelectorThreadLoadBalancer selectorThreadChoose;

        JNioAcceptThread(JSelectorThreadLoadBalancer loadBalancer, String host, int port) throws IOException {
            this.host = host;
            this.port = port;
            selectorThreadChoose = loadBalancer;
            acceptSelector = Selector.open();

            // ServerSocketChannel需设置非阻塞模式且只监听SelectionKey.OP_ACCEPT事件
            ServerSocketChannel ssc = ServerSocketChannel.open();
            ssc.socket().setReuseAddress(true);
            ssc.configureBlocking(false);
            ssc.bind(new InetSocketAddress(this.host, this.port));
            ssc.register(acceptSelector, SelectionKey.OP_ACCEPT);
        }

        @Override
        public void run() {
            try {
                while (!stopped.get()) {
                    acceptSelector.select();

                    Iterator<SelectionKey> it = acceptSelector.selectedKeys().iterator();
                    while (it.hasNext()) {
                        SelectionKey key = it.next();
                        it.remove();

                        if (!key.isValid()) {
                            continue;
                        }

                        if (key.isAcceptable()) {
                            ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
                            SocketChannel sc = ssc.accept();
                            Socket socket = sc.socket();
                            socket.setKeepAlive(true);
                            socket.setTcpNoDelay(true);

                            // 接受客户端连接并将客户端连接通信交由给QSelectorThread处理
                            doAddAccept(sc, selectorThreadChoose.nextSelectorThread());
                        }
                    }
                }
            } catch (Throwable e) {
                LOGGER.error("JNioAcceptThread exit dut to uncaught error", e);
            }
        }

        private void doAddAccept(SocketChannel sc, JNioSelectorThread selectorThread) throws IOException {
            selectorThread.doAddAccept(sc);
        }
    }

    /**
     * QSelectorThreadLoadBalancer负责QSelectorThread线程均匀的处理客户端连接
     * */
    private class JSelectorThreadLoadBalancer {

        private Set<JNioSelectorThread> selectorThreads;

        private Iterator<JNioSelectorThread> iterator;

        public JSelectorThreadLoadBalancer(Set<JNioSelectorThread> selectorThreads) {
            this.selectorThreads = selectorThreads;
            iterator = selectorThreads.iterator();
        }

        public JNioSelectorThread nextSelectorThread() {
            if (!iterator.hasNext()) {
                iterator = selectorThreads.iterator();
            }
            return iterator.next();
        }
    }

    /**
     * 负责客户端连接
     * */
    private class JNioSelectorThread extends Thread {

        private Selector selector;

        private BlockingQueue<SocketChannel> acceptQueue;

        public JNioSelectorThread(int size) throws IOException {
            selector = Selector.open();
            if (size <= 0) {
                acceptQueue = new LinkedBlockingQueue<>();
            } else {
                acceptQueue = new ArrayBlockingQueue<>(size);
            }
        }

        @Override
        public void run() {
            try {
                while (!stopped.get()) {
                    select();
                    processAcceptConnection();
                }
            } catch (Throwable e) {
                LOGGER.error("JNioSelectorThread exit dut to uncaught error", e);
            }

        }

        private void select() {
            try {
                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (!stopped.get() && iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isReadable()) {
                        handleSocketRead(key);
                    } else if (key.isWritable()) {
                        handleSocketWrite(key);
                    } else {
                        LOGGER.warn("Unexpected state in select! " + key.interestOps());
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Got an IOException while selector thread selecting!", e);
            }
        }

        private void processAcceptConnection() throws IOException {
            while (!stopped.get()) {
                SocketChannel sc = acceptQueue.poll();
                if (sc == null) {
                    break;
                }
                sc.configureBlocking(false);
                SelectionKey sk = sc.register(selector, SelectionKey.OP_READ);
                /**
                 * 每个客户端对应一个{@link QFrameBuffer}
                 * */
                JFrameBuffer buffer = new JFrameBuffer(sc, sk, maxReadBufferBytes, serializer);
                sk.attach(buffer);
            }
        }

        public void doAddAccept(SocketChannel sc) throws IOException {
            if (!stopped.get()) {
                acceptQueue.add(sc);
                // 唤醒Selector[由于Selector.select()阻塞]
                selector.wakeup();
            } else {
                sc.close();
            }
        }

        private void handleSocketRead(SelectionKey key) {
            JFrameBuffer buffer = (JFrameBuffer) key.attachment();
            if (buffer != null) {
                // Socket读取异常处理:
                // 1: 关闭Socket
                // 2: SelectionKey.cancel()
                if (!buffer.doRead()) {
                    buffer.cleanupSelectionKey(key);
                    return;
                }

                // Socket数据包读取完成后,交由业务线程处理
                // Note:
                //     业务线程处理异常,关闭Socket
                if (buffer.isFrameFullyRead()) {
                    if (workExecutor != null) {
                        try {
                            workExecutor.submit(() -> buffer.invoke());
                        } catch (RejectedExecutionException e) {
                            buffer.cleanupSelectionKey(key);
                        }
                    } else {
                        buffer.invoke();
                    }
                }
            }
        }

        private void handleSocketWrite(SelectionKey key) {
            JFrameBuffer buffer = (JFrameBuffer) key.attachment();
            if (buffer != null) {
                // Socket写数据失败, 关闭Socket
                if (!buffer.doWrite()) {
                    buffer.cleanupSelectionKey(key);
                }
            }
        }
    }

    public JNioReactorServer(JServerArgs args) throws IOException {
        if (args.maxReadBufferBytes <= 0) {
            maxReadBufferBytes = Long.MAX_VALUE;
        } else {
            maxReadBufferBytes = args.maxReadBufferBytes;
        }
        for (int i = 0; i < args.getSelectorThreadNumber(); ++i) {
            selectorThreads.add(new JNioSelectorThread(args.getQueueSizePerSelectorThread()));
        }
        acceptThread = new JNioAcceptThread(new JSelectorThreadLoadBalancer(selectorThreads), args.getHost(), args.getPort());
        workExecutor = new ThreadPoolExecutor(args.getMinWorkerThread(), args.getMaxWorkerThread(), 5, TimeUnit.MINUTES,
                                              new SynchronousQueue<>(), JSocketUtils.buildThreadFactory("worker-thread-%d", false));
        serializer = args.getSerializer();
    }

    private void startThread() {
        stopped.set(false);
        selectorThreads.forEach(JNioSelectorThread::start);
        acceptThread.start();
    }

    private void waitForShutdown() {
        try {
            joinThreads();
            shutdownGracefully();
        } catch (InterruptedException e) {
            LOGGER.error("Interrupted while joining threads!", e);
        }
    }

    private void joinThreads() throws InterruptedException {
        acceptThread.join();
        for (JNioSelectorThread selectorThread : selectorThreads) {
            selectorThread.join();
        }
    }

    private void shutdownGracefully() throws InterruptedException {
        workExecutor.shutdown();
        while (workExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
            workExecutor.shutdownNow();
        }
    }

    public void serve() {
        startThread();
        waitForShutdown();
    }


    public static void main(String[] args) throws Exception {
        KryoSerializer serializer = new KryoSerializer(Message.class, MessageAck.class);
        JServerArgs serverArgs = new JServerArgs();
        serverArgs.setSerializer(serializer);
        serverArgs.setSelectorThreadNumber(4);
        serverArgs.setQueueSizePerSelectorThread(10);
        serverArgs.setMinWorkerThread(5);
        serverArgs.setMaxWorkerThread(10);
        serverArgs.setHost("127.0.0.1");
        serverArgs.setPort(6721);

        JNioReactorServer server = new JNioReactorServer(serverArgs);

        server.serve();
    }
}
