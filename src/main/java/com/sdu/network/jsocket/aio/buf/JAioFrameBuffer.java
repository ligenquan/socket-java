package com.sdu.network.jsocket.aio.buf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.Future;


/**
 * {@link JAioFrameBuffer} 负责Socket数据读取
 *
 *   1: 拆包处理(read方法)
 *
 *   2:
 * @author hanhan.zhang
 * */
public class JAioFrameBuffer {

    private AsynchronousSocketChannel asyncSocketChannel;

    private ByteBuffer readBuffer;


    // 最初状态: 读取包头状态
    private JFrameBufferState state = JFrameBufferState.READING_FRAME_SIZE;

    public JAioFrameBuffer(AsynchronousSocketChannel asyncSocketChannel, ByteBuffer readBuffer) {
        this.asyncSocketChannel = asyncSocketChannel;
        this.readBuffer = readBuffer;
    }

    public void closeChannel() throws IOException {
        if (asyncSocketChannel != null) {
            asyncSocketChannel.close();
        }
    }

    public AsynchronousSocketChannel getAsyncSocketChannel() {
        return asyncSocketChannel;
    }

    public boolean hasReadRemaining() {
        return readBuffer.hasRemaining() && state == JFrameBufferState.READ_FRAME_COMPLETE ;
    }

    public void preRead() {
        if (state == JFrameBufferState.READ_FRAME_COMPLETE) {
            state = JFrameBufferState.READING_FRAME;
        }
    }

    public byte[] read() {
        // 记录Buffer最初状态
        int position = readBuffer.position();
        int limit = readBuffer.limit();
        readBuffer.flip();

        // FrameBuffer.state = READING_FRAME_SIZE, 表示读取新数据包
        if (state == JFrameBufferState.READING_FRAME_SIZE) {
            if (readBuffer.limit() - readBuffer.position() < 4) {
                // buffer长度是不够首部长度[4个字节], 还原Buffer最初状态并继续读取Socket数据
                readBuffer.position(position);
                readBuffer.limit(limit);
                return null;
            }

            state = JFrameBufferState.READING_FRAME;
        }

        // FrameBuffer.state = READING_FRAME, 表示读取数据包体
        if (state == JFrameBufferState.READING_FRAME) {
            // 读取报头
            int packageHead = readBuffer.getInt();
            if (readBuffer.remaining() < packageHead) {
                readBuffer.position(position);
                readBuffer.limit(limit);
                return null;
            }

            // 读取包体
            byte []bytes = new byte[packageHead];
            readBuffer.get(bytes);
            state = JFrameBufferState.READ_FRAME_COMPLETE;
            return bytes;
        }

        throw new IllegalStateException("illegal frame buffer state");
    }


    public void compactReadBuffer() {
        readBuffer.compact();
    }

    public ByteBuffer getReadBuffer() {
        return readBuffer;
    }

    public Future<Integer> write(String content) {
        ByteBuffer buffer = ByteBuffer.allocate(content.length() + 4);
        buffer.putInt(content.length());
        buffer.put(content.getBytes());
        buffer.flip();
        return asyncSocketChannel.write(buffer);
    }

    // Buffer读取状态
    private enum JFrameBufferState {
        // 读Frame消息头,实际是4字节表示Frame长度(解决Socket粘包/拆包问题)
        READING_FRAME_SIZE,
        // 读Frame消息体
        READING_FRAME,
        // 读满包(Socket数据包读取完成)
        READ_FRAME_COMPLETE
    }

}
