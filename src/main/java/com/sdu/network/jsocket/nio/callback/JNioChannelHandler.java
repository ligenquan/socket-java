package com.sdu.network.jsocket.nio.callback;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;

/**
 * @author hanhan.zhang
 * */
public interface JNioChannelHandler {

    public List<Object> channelRead(SocketChannel socketChannel, int readSize, ByteBuffer buffer) throws IOException;

    public void channelWrite(SocketChannel socketChannel, Object obj, ByteBuffer buffer) throws IOException;

}
