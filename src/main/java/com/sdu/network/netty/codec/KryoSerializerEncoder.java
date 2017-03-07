package com.sdu.network.netty.codec;


import com.sdu.network.netty.codec.kryo.KryoSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author hanhan.zhang
 * */
public class KryoSerializerEncoder extends MessageToByteEncoder<Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(KryoSerializerEncoder.class);

    private KryoSerializer serializer;

    public KryoSerializerEncoder(KryoSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        try {
            serializer.encode(out, msg);
        } catch (IOException e) {
            LOGGER.error("object serialize to bytes exception", e);
        }
    }
}
