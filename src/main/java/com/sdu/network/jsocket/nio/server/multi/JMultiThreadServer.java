package com.sdu.network.jsocket.nio.server.multi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One by One线程服务模型
 *
 * 1: 主线程负责接收客户端连接请求
 *
 * 2: 客户端的每个连接数据通信创建一个新线程
 *
 * @author hanhan.zhang
 * */
public class JMultiThreadServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JMultiThreadServer.class);

    // 监听客户端连接
    private Selector _acceptSelector;
    // 监听客户端数据读写
    private Selector _rwSelector;
    //
    private ServerSocketChannel _ssc;
    private AtomicBoolean _stop = new AtomicBoolean(true);
    // 工作线程
    private Set<Thread> workerThreads = new HashSet<>();

    public void serve(String host, int port) throws IOException {
        _acceptSelector = Selector.open();
        _rwSelector = Selector.open();
        // 初始化ServerSocketChannel
        _ssc = ServerSocketChannel.open();
        // 必须设置为非阻塞
        _ssc.configureBlocking(false);
        // 设置ServerSocket属性
        _ssc.socket().setReuseAddress(true);
        // 注册通道并监听OP_ACCEPT事件
        _ssc.register(_acceptSelector, SelectionKey.OP_ACCEPT);
        _ssc.bind(new InetSocketAddress(host, port));
        if (_ssc.isOpen()) {
            _stop.set(false);
        }
        while (!_stop.get()) {
            _acceptSelector.select();
            Iterator<SelectionKey> it = _acceptSelector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                // 移除通道事件
                it.remove();
                if (key.isAcceptable()) {
                    doAccept(key, _rwSelector);
                }
            }
        }
    }

    private void doAccept(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
        SocketChannel sc = ssc.accept();
        // 每个连接创建一个线程处理
        Thread socketThread = new SocketThread(sc, selector);
        workerThreads.add(socketThread);
        socketThread.start();
    }

    private void doRead(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        // 读取数据包头
        ByteBuffer buffer = ByteBuffer.allocate(4);
        int readSize = sc.read(buffer);
        if (readSize == -1) {
            // Note:
            //  SocketChannel.read() == -1, 有两种情况:
            //  1: 连接没有打开
            //  2: 另一端已关闭
            InetSocketAddress socketAddress = (InetSocketAddress) sc.getRemoteAddress();
            sc.close();
            System.out.println("远端连接[" + socketAddress.toString() + "]已关闭, 关闭SocketChannel");
            return;
        }
        if (buffer.remaining() == 0) {
            // 读取数据包头成功, 开始读取数据包体
            buffer.flip();
            int size = buffer.getInt();
            // 重新分配Buffer
            buffer = ByteBuffer.allocate(size);
            sc.read(buffer);
            if (buffer.remaining() == 0) {
                buffer.flip();
                // 读取数据包体成功, 开始业务处理并反馈客户端
                LOGGER.info("server receive : {}", new String(buffer.array()));
                // 响应客户端(注册写事件)
                preWrite(key);
            }
        }
    }

    private void doWrite(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        sc.write(ByteBuffer.wrap("OK".getBytes()));
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
        _stop.set(true);
        _acceptSelector.close();
        _rwSelector.select();
        _ssc.close();
    }

    /**
     * Socket通信线程(客户端数据通信由一个线程负责处理)
     * */
    private class SocketThread extends Thread {
        private SocketChannel _sc;
        private Selector _selector;

        public SocketThread(SocketChannel sc, Selector selector) {
            this._sc = sc;
            this._selector = selector;
        }

        @Override
        public void run() {
            try {
                if (_sc.isConnected() && !_stop.get()) {
                    _sc.configureBlocking(false);
                    // 设置Socket属性
                    _sc.socket().setKeepAlive(true);
                    _sc.socket().setTcpNoDelay(true);
                    // 注册通道事件
                    _sc.register(_selector, SelectionKey.OP_READ);
                    while (!_stop.get()) {
                        _selector.select();
                        Iterator<SelectionKey> it = _selector.selectedKeys().iterator();
                        while (it.hasNext()) {
                            SelectionKey key = it.next();
                            // 移除通道事件
                            it.remove();
                            if (key.isReadable()) {
                                doRead(key);
                            } else if (key.isWritable()) {
                                doWrite(key);
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
        JMultiThreadServer multiThreadServer = new JMultiThreadServer();
        multiThreadServer.serve("127.0.0.1", 6721);
    }
}
