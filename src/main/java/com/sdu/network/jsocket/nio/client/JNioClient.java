package com.sdu.network.jsocket.nio.client;


import com.sdu.network.jsocket.nio.handle.JChannelHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * @author hanhan.zhang
 * */
public class JNioClient {

    private SocketChannel _sc;

    private Selector _selector;

    private AtomicBoolean _started = new AtomicBoolean(true);

    private JChannelHandler _handler;

    public JNioClient(JChannelHandler handler) {
        this._handler = handler;
    }

    public void start(String host, int port) throws IOException {
        _selector = Selector.open();
        // 设置SocketChannel
        _sc = SocketChannel.open();
        _sc.configureBlocking(false);
        // 注册SocketChannel感兴趣事件
        _sc.register(_selector, SelectionKey.OP_READ | SelectionKey.OP_CONNECT | SelectionKey.OP_WRITE);
        // 设置Socket选项
        _sc.socket().setKeepAlive(true);
        _sc.socket().setTcpNoDelay(true);
        // 连接服务器
        _sc.connect(new InetSocketAddress(host, port));
        while (_started.get()) {
            _selector.select();
            Iterator<SelectionKey> it = _selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();
                if (!key.isValid()) {
                    continue;
                }
                if (key.isConnectable()) {
                    doConnect(key);
                } else if (key.isReadable()) {
                    doRead(key);
                } else if (key.isWritable()) {
                    doWrite(key);
                }
            }
        }
    }

    private void doConnect(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        if (sc.isConnectionPending()) {
            boolean finished = sc.finishConnect();
            if (finished) {
                _started.set(finished);
                // 更改SelectionKey的感兴趣事件, SelectionKey.OP_WRITE
                key.interestOps(SelectionKey.OP_WRITE);
            }
        }
    }

    private void doRead(SelectionKey key) throws IOException{
        SocketChannel sc = (SocketChannel) key.channel();
        _handler.fireRead(sc);
        // 更改SelectionKey的感兴趣事件, SelectionKey.OP_WRITE
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void doWrite(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        _handler.fireWrite(sc);
        // 更改SelectionKey的感兴趣事件, SelectionKey.OP_READ
        key.interestOps(SelectionKey.OP_READ);
    }

    public void stop() throws IOException {
        if (_sc != null) {
            _sc.close();
        }
        if (_selector != null) {
            _selector.close();
        }
        _started.set(false);
    }
}
