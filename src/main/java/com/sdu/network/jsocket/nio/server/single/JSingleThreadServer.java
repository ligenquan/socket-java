package com.sdu.network.jsocket.nio.server.single;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单线程服务模型:
 *
 *  Server端单线线程用于处理客户端的连接请求
 *
 *  @apiNote
 *
 *     SelectionKey.OP_WRITE: 只要通道空闲,就触发写事件(容易造成CPU假虚高)
 *
 * @author hanhan.zhang
 * */
public class JSingleThreadServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JSingleThreadServer.class);

    // 监听通道事件
    private Selector _selector;

    private ServerSocketChannel _ssc;
    //
    private AtomicBoolean _stopped = new AtomicBoolean(true);

    public void serve(String host, int port) throws IOException {
        _selector = Selector.open();
        // 初始化ServerSocketChannel
        _ssc = ServerSocketChannel.open();
        // 必须设置为非阻塞
        _ssc.configureBlocking(false);
        // 设置ServerSocket属性
        _ssc.socket().setReuseAddress(true);
        // 注册通道并监听OP_ACCEPT事件
        _ssc.register(_selector, SelectionKey.OP_ACCEPT);
        _ssc.bind(new InetSocketAddress(host, port));
        if (_ssc.isOpen()) {
            _stopped.set(false);
        }
        // 处理客户端连接请求
        while (!_stopped.get()) {
            _selector.select();
            Iterator<SelectionKey> iterator = _selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                // 移除通道事件
                iterator.remove();
                if(key.isAcceptable()) {
                    doAccept(key, _selector);
                } else if (key.isReadable()) {
                    doRead(key);
                } else if (key.isWritable()) {
                    doWrite(key);
                }
            }
        }
    }

    private void doAccept(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
        SocketChannel sc = ssc.accept();
        if (!sc.isConnected()) {
            return;
        }
        sc.configureBlocking(false);
        sc.socket().setKeepAlive(true);
        sc.socket().setTcpNoDelay(true);
        // 注册通道
        sc.register(selector, SelectionKey.OP_READ);
    }

    private void doRead(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        InetSocketAddress socketAddress = (InetSocketAddress) sc.getRemoteAddress();
        String remoteAddress = socketAddress.getHostName() + ":" + socketAddress.getPort();
        // 读取数据包头
        ByteBuffer buffer = ByteBuffer.allocate(4);
        int readSize = sc.read(buffer);
        if (readSize == -1) {
            // Note:
            //  SocketChannel.read() == -1, 有两种情况:
            //  1: 连接没有打开
            //  2: 另一端已关闭
            sc.close();
            LOGGER.info("remote client {} close socket", remoteAddress);
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
                LOGGER.info("single thread server receive : {}, client = {}", new String(buffer.array()), remoteAddress);
                // 读取客户端数据完成,响应客户端(注册写事件)
                preWrite(key);
            }
        }
    }

    private void doWrite(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        sc.write(ByteBuffer.wrap("OK".getBytes()));
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
        _stopped.set(true);
        _selector.close();
        _ssc.close();
    }

    public static void main(String[] args) throws IOException {
        JSingleThreadServer server = new JSingleThreadServer();
        server.serve("127.0.0.1", 6721);
    }
}
