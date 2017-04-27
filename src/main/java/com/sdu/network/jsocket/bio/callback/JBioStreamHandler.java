package com.sdu.network.jsocket.bio.callback;

import java.io.IOException;
import java.net.Socket;

/**
 * Note:
 *
 *  {@link java.io.ObjectInputStream}
 *
 * @author hanhan.zhang
 * */
public interface JBioStreamHandler {

    void read(Socket sc) throws IOException;

    void write(Socket sc) throws IOException;
}

