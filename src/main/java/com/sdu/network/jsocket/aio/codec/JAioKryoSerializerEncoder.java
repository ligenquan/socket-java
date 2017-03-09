package com.sdu.network.jsocket.aio.codec;


import com.sdu.network.serializer.KryoSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Thread safe, Suggest : Single Object
 *
 * @author hanhan.zhang
 * */
public class JAioKryoSerializerEncoder {

    private static final Logger LOGGER = LoggerFactory.getLogger(JAioKryoSerializerEncoder.class);

    private KryoSerializer serializer;

    public JAioKryoSerializerEncoder(KryoSerializer serializer) {
        this.serializer = serializer;
    }

    public void encode(Object msg, ByteBuffer out) {
        try {
            out.clear();
            serializer.encode(out, msg);
            out.flip();
        } catch (IOException e) {
            LOGGER.error("object serialize to bytes exception", e);
        }
    }
}
