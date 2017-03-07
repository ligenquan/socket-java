package com.sdu.network.jsocket.aio.handle;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;

/**
 * @author hanhan.zhang
 * */
public interface JAioChannelHandler {

    public void fireConnect(AsynchronousSocketChannel asyncSocketChannel);

    public void fireAccept(AsynchronousSocketChannel asyncSocketChannel);

    public void fireRead(AsynchronousSocketChannel asyncSocketChannel, int readSize, ByteBuffer buffer);

    public void fireWrite(AsynchronousSocketChannel asyncSocketChannel, int writeSize, ByteBuffer buffer);

    public void occurException(AsynchronousSocketChannel asyncSocketChannel, Throwable t);

}
