package com.sdu.network.jsocket.aio.callback;

import com.sdu.network.bean.MessageAck;
import com.sdu.network.codec.JSocketDataDecoder;
import com.sdu.network.serializer.KryoSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.List;

/**
 * @author hanhan.zhang
 * */
public class JAioClientReadHandler implements CompletionHandler<Integer, ByteBuffer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JAioClientReadHandler.class);

    private JSocketDataDecoder decoder;

    private AsynchronousSocketChannel socketChannel;

    public JAioClientReadHandler(AsynchronousSocketChannel socketChannel, KryoSerializer serializer) {
        this.socketChannel = socketChannel;
        decoder = new JSocketDataDecoder(serializer);
    }

    @Override
    public void completed(Integer result, ByteBuffer attachment) {
        try {
            String threadName = Thread.currentThread().getName();
            InetSocketAddress socketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
            String clientAddress = socketAddress.getHostString() + ":" + socketAddress.getPort();
            if (result >= 0) {
                List<Object> messageList = decoder.decode(attachment);
                if (messageList != null && messageList.size() > 0) {
                    messageList.forEach(object -> {
                        if (object.getClass() == MessageAck.class) {
                            MessageAck msg = (MessageAck) object;
                            LOGGER.info("线程[{}]收到来自服务器{}的{}消息确认", threadName, clientAddress, msg.getMsgId());
                        }
                    });
                }
                // 递归方式监听客户端发送的数据
                socketChannel.read(attachment, attachment, this);
            } else {
                // result = -1, 客户端已关闭连接
                LOGGER.info("{}服务器已关闭", clientAddress);
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
