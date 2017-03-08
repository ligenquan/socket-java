package com.sdu.network.jsocket.aio.handle;

import com.sdu.network.jsocket.aio.buf.JAioFrameBuffer;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;

/**
 * @author hanhan.zhang
 * */
public interface JAioChannelHandler {

    public void fireConnectComplete(AsynchronousSocketChannel asyncSocketChannel);

    public void fireAcceptComplete(AsynchronousSocketChannel asyncSocketChannel);

    public void fireReadComplete(AsynchronousSocketChannel asyncSocketChannel, int readSize, JAioFrameBuffer jAioFrameBuffer);

    public void fireWriteComplete(AsynchronousSocketChannel asyncSocketChannel, int writeSize, ByteBuffer buffer);

    public void occurException(AsynchronousSocketChannel asyncSocketChannel, Throwable t);

}
