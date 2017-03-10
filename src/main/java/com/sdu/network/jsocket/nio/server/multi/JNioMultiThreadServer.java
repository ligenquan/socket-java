package com.sdu.network.jsocket.nio.server.multi;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.sdu.network.codec.JSocketDataDecoder;
import com.sdu.network.codec.JSocketDataEncoder;
import com.sdu.network.jsocket.aio.bean.Message;
import com.sdu.network.jsocket.aio.bean.MessageAck;
import com.sdu.network.jsocket.nio.callback.JNioChannelHandler;
import com.sdu.network.jsocket.nio.callback.impl.JDefaultNioChannelHandler;
import com.sdu.network.jsocket.utils.JSocketUtils;
import com.sdu.network.serializer.KryoSerializer;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One by One线程服务模型
 *
 * 1: 主线程负责接收客户端连接请求
 *
 * 2: 客户端的每个连接数据通信创建一个新线程
 *
 * @author hanhan.zhang
 * */
public class JNioMultiThreadServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JNioMultiThreadServer.class);

    // 监听客户端连接
    private Selector acceptSelector;

    private ServerSocketChannel ssc;
    private AtomicBoolean stopped = new AtomicBoolean(true);
    // 工作线程
    private Set<Thread> workerThreads = new HashSet<>();

    private JNioChannelHandler channelHandler;

    private int readBufferSize;

    private AtomicInteger threadNum = new AtomicInteger();

    public JNioMultiThreadServer(JNioChannelHandler channelHandler, int readBufferSize) {
        this.channelHandler = channelHandler;
        this.readBufferSize = readBufferSize;
    }

    public void serve(String host, int port) throws IOException {
        acceptSelector = Selector.open();
        // 初始化ServerSocketChannel
        ssc = ServerSocketChannel.open();
        // 必须设置为非阻塞
        ssc.configureBlocking(false);

        // 设置ServerSocket属性
        ssc.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        ssc.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        ssc.setOption(StandardSocketOptions.SO_RCVBUF, 1024);


        // 注册通道并监听OP_ACCEPT事件
        ssc.register(acceptSelector, SelectionKey.OP_ACCEPT);
        ssc.bind(new InetSocketAddress(host, port));
        if (ssc.isOpen()) {
            stopped.set(false);
        }

        while (!stopped.get()) {
            acceptSelector.select();
            Iterator<SelectionKey> it = acceptSelector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                // 移除通道事件
                it.remove();
                if (key.isAcceptable()) {
                    doAccept(key);
                }
            }
        }
    }

    private void doAccept(SelectionKey key) throws IOException {
        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
        SocketChannel sc = ssc.accept();
        // 每个连接创建一个线程处理
        Thread socketThread = new JNioSocketThread(sc);
        workerThreads.add(socketThread);
        socketThread.start();
    }

    private void doRead(SelectionKey key, ByteBuffer readBuffer, Set<String> receiveMsg) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        String clientAddress = JSocketUtils.getClientAddress(sc);
        int readSize = sc.read(readBuffer);
        List<Object> msgList = channelHandler.channelRead(sc, readSize, readBuffer);

        if (msgList != null && msgList.size() > 0) {
            msgList.forEach(object -> {
                if (object.getClass() == Message.class) {
                    Message msg = (Message) object;
                    LOGGER.info("线程[{}]收到来自客户端{}的消息:{}", Thread.currentThread().getName(), clientAddress, msg.toString());
                    receiveMsg.add(msg.getMsgId());
                }
            });

            // 客户端数据读取结束, 通道监听事件改为OP_WRITE
            preWrite(key);
        }
    }

    private void doWrite(SelectionKey key, Set<String> receivedMsg) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        ByteBuffer writeBuffer = ByteBuffer.allocate(1024);
        if (receivedMsg != null && receivedMsg.size() > 0) {
            Iterator<String> it = receivedMsg.iterator();
            while (it.hasNext()) {
                String msgId = it.next();
                it.remove();
                MessageAck msgAck = new MessageAck(msgId);
                channelHandler.channelWrite(sc, msgAck, writeBuffer);
                sc.write(writeBuffer);
            }
        }

        // 响应客户端结束,改变通道监听事件为OP_READ(监听客户端再次发送的数据请求)
        preRead(key);
    }

    private void preWrite(SelectionKey key) {
        // 更改SelectionKey关联的SocketChannel关注事件，更改为OP_WRITE
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void preRead(SelectionKey key) {
        // 更改SelectionKey关联的SocketChannel关注事件，更改为OP_READ
        key.interestOps(SelectionKey.OP_READ);
    }

    public void close() throws IOException {
        stopped.set(true);
        acceptSelector.close();
        ssc.close();
        workerThreads.forEach(Thread::interrupt);
    }

    /**
     * Socket通信线程(客户端数据通信由一个线程负责处理)
     * */
    private class JNioSocketThread extends Thread {
        private SocketChannel sc;

        private Selector selector;

        private ByteBuffer readBuffer;

        private Set<String> receivedMsg;

        JNioSocketThread(SocketChannel sc) throws IOException {
            super("socket-thread-" + threadNum.incrementAndGet());
            this.sc = sc;
            this.selector = Selector.open();
            this.readBuffer = ByteBuffer.allocate(readBufferSize);
            this.receivedMsg = Sets.newConcurrentHashSet();
        }

        @Override
        public void run() {
            try {
                if (sc.isConnected() && !stopped.get()) {
                    sc.configureBlocking(false);
                    // 设置Socket属性
                    sc.setOption(StandardSocketOptions.SO_KEEPALIVE, true);

                    // 注册通道事件
                    sc.register(selector, SelectionKey.OP_READ);
                    while (!stopped.get()) {
                        selector.select();
                        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                        while (it.hasNext()) {
                            SelectionKey key = it.next();
                            // 移除通道事件
                            it.remove();
                            if (key.isReadable()) {
                                doRead(key, readBuffer, receivedMsg);
                            } else if (key.isWritable()) {
                                doWrite(key, receivedMsg);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                //
            }
        }
    }

    public static void main(String[] args) throws IOException {
        KryoSerializer serializer = new KryoSerializer(Message.class, MessageAck.class);
        JSocketDataEncoder encoder = new JSocketDataEncoder(serializer);
        JSocketDataDecoder decoder = new JSocketDataDecoder(serializer);
        JDefaultNioChannelHandler channelHandler = new JDefaultNioChannelHandler(decoder, encoder);
        JNioMultiThreadServer multiThreadServer = new JNioMultiThreadServer(channelHandler, 1024);
        multiThreadServer.serve("127.0.0.1", 6721);
    }
}
