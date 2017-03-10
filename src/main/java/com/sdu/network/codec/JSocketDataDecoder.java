package com.sdu.network.codec;

import com.google.common.collect.Lists;
import com.sdu.network.serializer.KryoSerializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Thread safe, Suggest : Single Object
 *
 * @author hanhan.zhang
 * */
public class JSocketDataDecoder {

    private static final int SOCKET_HEAD_LENGTH = 4;

    private KryoSerializer serializer;

    public JSocketDataDecoder(KryoSerializer serializer) {
        this.serializer = serializer;
    }

    public List<Object> decode(ByteBuffer buffer) throws IOException {
        buffer.flip();

        List<Object> msgList = Lists.newLinkedList();

        boolean read = false;
        // 循环读取
        while (buffer.hasRemaining()) {
            // 记录当前Buffer的position, 当发生读半包时将Buffer还原
            buffer.mark();

            if (buffer.remaining() < SOCKET_HEAD_LENGTH) {
                // 少于4个字节
                break;
            }

            // 读取头部
            int bodyLength = buffer.getInt();
            if (buffer.remaining() < bodyLength) {
                // 发生读半包, buffer还原至初始状态, 继续读取Socket通道数据
                buffer.reset();
                break;
            }

            byte[] messageBody = new byte[bodyLength];
            buffer.get(messageBody);
            Object obj = serializer.decode(messageBody);
            msgList.add(obj);
            read = true;
        }

        if (read) {
            buffer.compact();
        }

        return msgList;
    }
}