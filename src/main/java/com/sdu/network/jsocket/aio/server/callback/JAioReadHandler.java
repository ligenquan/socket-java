package com.sdu.network.jsocket.aio.server.callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

/**
 * {@link JAioReadHandler}职责:
 *
 *  1: 负责以递归的方式读取客户端数据
 *
 *  2: 负责对客户端做出响应
 *
 * @author hanhan.zhang
 * */
public class JAioReadHandler implements CompletionHandler<Integer, ByteBuffer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JAioReadHandler.class);

    private AsynchronousSocketChannel socketChannel;

    public JAioReadHandler(AsynchronousSocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

    @Override
    public void completed(Integer result, ByteBuffer attachment) {
        try {
            InetSocketAddress socketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
            String clientAddress = socketAddress.getHostString() + ":" + socketAddress.getPort();
            String threadName = Thread.currentThread().getName();
            if (result >= 0) {
                attachment.flip();
                byte []bytes = new byte[attachment.limit() - attachment.position()];
                attachment.get(bytes);
                LOGGER.info("ioThread[{}] receive client[{}] message : {}", threadName, clientAddress, new String(bytes));
                attachment.compact();
//                attachment.put("OK".getBytes());
//                attachment.flip();
//                socketChannel.write(attachment);
//                attachment.flip();
                // 递归方式监听客户端发送的数据
                socketChannel.read(attachment, attachment, this);
            } else {
                // result = -1, 客户端已关闭连接
                LOGGER.info("client {} closed, ioThread[[]] close async socket channel", clientAddress, threadName);
                socketChannel.close();
                attachment = null;
            }
        } catch (Exception e) {
            LOGGER.info("read exception", e);
        }
    }

    @Override
    public void failed(Throwable exc, ByteBuffer attachment) {

    }
}
