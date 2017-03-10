package com.sdu.network.jsocket.nio.callback.impl;

import com.sdu.network.codec.JSocketDataDecoder;
import com.sdu.network.codec.JSocketDataEncoder;
import com.sdu.network.jsocket.nio.callback.JNioChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.List;

/**
 * @author hanhan.zhang
 * */
public class JDefaultNioChannelHandler implements JNioChannelHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(JDefaultNioChannelHandler.class);

    private JSocketDataDecoder decoder;

    private JSocketDataEncoder encoder;

    public JDefaultNioChannelHandler(JSocketDataDecoder decoder, JSocketDataEncoder encoder) {
        this.decoder = decoder;
        this.encoder = encoder;
    }

    @Override
    public List<Object> channelRead(SocketChannel socketChannel, int readSize, ByteBuffer buffer) throws IOException {
        if (readSize < 0) {
            // 远端已关闭或尚未连接远端, 关闭通道
            InetSocketAddress socketAddress = ((InetSocketAddress)socketChannel.getRemoteAddress());
            String clientAddress = socketAddress.getHostString() + ":" + socketAddress.getPort();
            LOGGER.info("客户端{}已关闭连接, 关闭Socket通道.", clientAddress);
            socketChannel.close();
            return Collections.emptyList();
        }
        return decoder.decode(buffer);
    }

    @Override
    public void channelWrite(SocketChannel socketChannel, Object obj, ByteBuffer buffer) throws IOException {
        encoder.encode(obj, buffer);
    }
}
