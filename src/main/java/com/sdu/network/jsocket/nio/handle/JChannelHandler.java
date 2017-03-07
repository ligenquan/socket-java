package com.sdu.network.jsocket.nio.handle;

import java.io.IOException;
import java.nio.channels.SocketChannel;

/**
 *
 * @author hanhan.zhang
 * */
public interface JChannelHandler {

    public void fireRead(SocketChannel sc) throws IOException;

    public void fireWrite(SocketChannel sc) throws IOException;

}
