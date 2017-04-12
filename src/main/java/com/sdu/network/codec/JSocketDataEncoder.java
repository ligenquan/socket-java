package com.sdu.network.codec;


import com.sdu.network.serializer.KryoSerializer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Thread safe, Suggest : Single Object
 *
 * @author hanhan.zhang
 * */
public class JSocketDataEncoder {

    private KryoSerializer serializer;

    public JSocketDataEncoder(KryoSerializer serializer) {
        this.serializer = serializer;
    }

    public void encode(Object msg, ByteBuffer out) {
        try {
            out.clear();
            serializer.encode(out, msg);
            out.flip();
        } catch (Exception e) {
            // ignore
        }
    }

    public void encode(Object msg, OutputStream outputStream) {
        try {
            serializer.encode(outputStream, msg);
        } catch (IOException e) {
            // ignore
        }
    }
}
