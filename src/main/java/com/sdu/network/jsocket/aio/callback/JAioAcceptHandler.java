package com.sdu.network.jsocket.aio.callback;

import com.sdu.network.bean.Message;
import com.sdu.network.serializer.KryoSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

/**
 * {@link JAioAcceptHandler}职责:
 *
 *  1: 以递归方式接受客户端连接
 *
 *  2: 对接受的客户端连接注册读回调函数
 *
 * @author hanhan.zhang
 * */
public class JAioAcceptHandler implements CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JAioAcceptHandler.class);

    private int readBufferSize;

    public JAioAcceptHandler(int readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    @Override
    public void completed(AsynchronousSocketChannel socketChannel, AsynchronousServerSocketChannel attachment) {
        try {
            if (!socketChannel.isOpen()) {
                socketChannel.close();
                return;
            }

            // socket参数设置
            socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
            socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            socketChannel.setOption(StandardSocketOptions.SO_RCVBUF, 1024);
            socketChannel.setOption(StandardSocketOptions.SO_SNDBUF, 1024);

            // 客户端连接信息
            InetSocketAddress remoteAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
            String address = remoteAddress.getHostString() + ":" + remoteAddress.getPort();
            LOGGER.info("线程[{}]接受客户端{}连接", Thread.currentThread().getName(), address);

            // 注册读事件
            ByteBuffer buffer = ByteBuffer.allocate(readBufferSize);
            socketChannel.read(buffer, buffer, new JAioServerReadHandler(socketChannel, new KryoSerializer(Message.class)));
        } catch (IOException e) {
            LOGGER.error("accept exception", e);
        } finally {
            // 递归接受下一个连接
            attachment.accept(attachment, this);
        }

    }

    @Override
    public void failed(Throwable exc, AsynchronousServerSocketChannel attachment) {
        // 递归接受下一个连接
        attachment.accept(attachment, this);
    }
}
