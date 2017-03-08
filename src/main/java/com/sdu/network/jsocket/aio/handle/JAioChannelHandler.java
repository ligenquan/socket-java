package com.sdu.network.jsocket.aio.handle;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;

/**
 * @author hanhan.zhang
 * */
public interface JAioChannelHandler {

    public void fireConnectComplete(AsynchronousSocketChannel asyncSocketChannel);

    public void fireAcceptComplete(AsynchronousSocketChannel asyncSocketChannel);

    public void fireReadComplete(AsynchronousSocketChannel asyncSocketChannel, int readSize, ByteBuffer buffer);

    public void fireWriteComplete(AsynchronousSocketChannel asyncSocketChannel, int writeSize, ByteBuffer buffer);

    public void occurException(AsynchronousSocketChannel asyncSocketChannel, Throwable t);

}
