package com.sdu.network.jsocket.aio.callback;

import com.sdu.network.jsocket.aio.bean.Message;
import com.sdu.network.jsocket.aio.bean.MessageAck;
import com.sdu.network.jsocket.aio.codec.JAioKryoSerializerDecoder;
import com.sdu.network.jsocket.aio.codec.JAioKryoSerializerEncoder;
import com.sdu.network.serializer.KryoSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.List;

/**
 * {@link JAioServerReadHandler}职责:
 *
 *  1: 负责以递归的方式读取客户端数据
 *
 *  2: 负责对客户端做出响应
 *
 *  Note:
 *
 *    1: JAioServerReadHandler对应一个连接通道且每个通道拥有一个ByteBuffer用于数据读取和发送
 *
 *    2: JAioServerReadHandler完成对Socket粘包/拆包处理
 *
 * @author hanhan.zhang
 * */
public class JAioServerReadHandler implements CompletionHandler<Integer, ByteBuffer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JAioServerReadHandler.class);

    private AsynchronousSocketChannel socketChannel;

    private JAioKryoSerializerDecoder decoder;

    private JAioKryoSerializerEncoder encoder;

    private ByteBuffer writeBuffer;

    public JAioServerReadHandler(AsynchronousSocketChannel socketChannel, KryoSerializer serializer) {
        this.socketChannel = socketChannel;
        decoder = new JAioKryoSerializerDecoder(serializer);
        encoder = new JAioKryoSerializerEncoder(serializer);
        writeBuffer = ByteBuffer.allocate(1024);
    }

    @Override
    public void completed(Integer result, ByteBuffer attachment) {
        try {
            InetSocketAddress socketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
            String clientAddress = socketAddress.getHostString() + ":" + socketAddress.getPort();
            String threadName = Thread.currentThread().getName();
            if (result >= 0) {
                List<Object> messageList = decoder.decode(attachment);
                if (messageList != null && messageList.size() > 0) {
                    messageList.forEach(object -> {
                        if (object.getClass() == Message.class) {
                            Message msg = (Message) object;
                            LOGGER.info("线程[{}]收到来自客户端{}的消息:{}", threadName, clientAddress, msg.toString());
                            // 响应客户端
                            MessageAck ack = new MessageAck(msg.getMsgId());
                            encoder.encode(ack, writeBuffer);
                            socketChannel.write(writeBuffer);
                        }

                    });
                }
                // 递归方式监听客户端发送的数据
                socketChannel.read(attachment, attachment, this);
            } else {
                // result = -1, 客户端已关闭连接
                LOGGER.info("客户端{}关闭连接, 线程[{}]关闭异步Socket通道", clientAddress, threadName);
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


    private void doReadBuffer(ByteBuffer buffer) {
        buffer.flip();
    }
}
