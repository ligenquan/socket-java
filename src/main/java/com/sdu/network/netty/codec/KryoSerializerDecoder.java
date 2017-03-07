package com.sdu.network.netty.codec;

import com.sdu.network.netty.codec.kryo.KryoSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * @author hanhan.zhang
 * */
public class KryoSerializerDecoder extends ByteToMessageDecoder {

    private static final Logger LOGGER = LoggerFactory.getLogger(KryoSerializerDecoder.class);

    private static final int SOCKET_HEAD_LENGTH = 4;

    private KryoSerializer serializer;

    public KryoSerializerDecoder(KryoSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 读取SOCKET数据包头
        if (in.readableBytes() < SOCKET_HEAD_LENGTH) {
            return;
        }

        // 读取SOCKET数据包头
        in.markReaderIndex();
        int bodyLength = in.readInt();

        if (bodyLength < 0) {
            ctx.close();
        }

        // 可读字节长度小于实际包体长度, 则发生读半包, 重置Buf的readerIndex为markedReaderIndex
        if (in.readableBytes() < bodyLength) {
            in.resetReaderIndex();
        } else {
            byte[] messageBody = new byte[bodyLength];
            in.readBytes(messageBody);
            try {
                Object obj = serializer.decode(messageBody);
                out.add(obj);
            } catch (IOException ex) {
                LOGGER.error("socket bytes serialize to object exception", ex);
            }
        }
    }
}
