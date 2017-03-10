package com.sdu.network.jsocket.nio.buf;

import com.sdu.network.bean.Message;
import com.sdu.network.bean.MessageAck;
import com.sdu.network.serializer.KryoSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * {@link JFrameBuffer}负责Socket通信数据读写
 *
 * @author hanhan.zhang
 * */
public class JFrameBuffer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JFrameBuffer.class);

    // 数据包最大长度
    private long MAX_FRAME_SIZE = Long.MAX_VALUE;

    // Socket读取状态
    private JFrameBufferState state = JFrameBufferState.READING_FRAME_SIZE;

    // 缓冲区[socket read/write]
    private ByteBuffer buffer;

    // QFrameBuffer关联的SocketChannel
    private SocketChannel channel;

    // SocketChannel注册的SelectorKey
    private SelectionKey selectionKey;

    private KryoSerializer serializer;

    public JFrameBuffer(SocketChannel channel, SelectionKey _key, long maxFrameSize, KryoSerializer serializer) {
        this.MAX_FRAME_SIZE = maxFrameSize;
        this.channel = channel;
        this.selectionKey = _key;

        // QFrameBuffer初始状态读取Socket数据包包头, 故Buffer的容量设置为4个字节
        this.buffer = ByteBuffer.allocate(4);
        this.serializer = serializer;
    }

    public void cleanupSelectionKey(SelectionKey key) {
        JFrameBuffer frameBuffer = (JFrameBuffer) key.attachment();
        if (frameBuffer != null) {
            frameBuffer.close();
        }
        key.cancel();
    }

    // Socket Read
    private boolean internalRead() {
        try {
            if (channel.read(buffer) < 0) {
                // Note:
                //      SocketChannel.read() == -1, 原因:
                //      1: Socket尚未建立连接
                //      2: Socket另一端已关闭
                return false;
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.warn("occur an IOException in internalRead!", e);
            return false;
        }
    }

    public boolean doRead() {
        // 读取Socket数据包包头[_buffer初始化长度为4个字节]
        if (state == JFrameBufferState.READING_FRAME_SIZE) {
            if (!internalRead()) {
                return false;
            }

            if (buffer.remaining() == 0) {
                buffer.flip();
                // 已读取4字节包头长度
                int frameSize = buffer.getInt();

                // Socket数据包长度大于最大缓冲区尺寸
                if (frameSize > MAX_FRAME_SIZE) {
                    LOGGER.error("Read a frame size of {}, which is bigger than the maximum allowable buffer size for ALL connections.", frameSize);
                    return false;
                }

                // 重新分配Buffer
                buffer = ByteBuffer.allocate(frameSize);
                // 更改QFrameBufferState状态[Socket数据包读取完成,开始读取Socket包体]
                state = JFrameBufferState.READING_FRAME;
            } else {
                // socket没有数据传输
                return true;
            }
        }

        // 读取Socket数据包包体
        if (state == JFrameBufferState.READING_FRAME) {
            if (!internalRead()) {
                return false;
            }

            // SelectionKey.OP_WRITE写事件, 只有通道空闲就会触发写事件[会造成CPU假高潮]
            // Note:
            //     selectionKey.interestOps(0)即取消注册的事件
            if (buffer.remaining() == 0) {
                selectionKey.interestOps(0);
                state = JFrameBufferState.READ_FRAME_COMPLETE;
            }
            return true;
        }

        LOGGER.error("read was called but state is invalid ({})", state);
        return false;
    }

    // SocketChannel数据写完,准备接收客户端数据
    private void prepareRead() {
        selectionKey.interestOps(SelectionKey.OP_READ);
        buffer = ByteBuffer.allocate(4);
        state = JFrameBufferState.READING_FRAME_SIZE;
    }

    public boolean doWrite() {
        if (state == JFrameBufferState.WRITING) {
            try {
                if (channel.write(buffer) < 0) {
                    // Note:
                    //      SocketChannel.write()
                    //      1: Socket写数据首先确保通道是Open状态
                    return false;
                }
            } catch (IOException e) {
                LOGGER.warn("occur an IOException during write!", e);
                return false;
            }

            // 数据写结束
            if (buffer.remaining() == 0) {
                prepareRead();
            }
            return true;
        }
        return false;
    }

    private void close() {
        try {
            channel.close();
            selectionKey.cancel();
        } catch (Exception e) {
            // ignore
        }
    }

    // Socket数据包是否完全读取
    public boolean isFrameFullyRead() {
        return state == JFrameBufferState.READ_FRAME_COMPLETE;
    }

    /**
     * 根据FrameBuffer当前状态修改SelectionKey关注事件
     * */
    private void changeSelectInterests() {
        if (state == JFrameBufferState.AWAITING_REGISTER_WRITE) {
            selectionKey.interestOps(SelectionKey.OP_WRITE);
            state = JFrameBufferState.WRITING;
        } else if (state == JFrameBufferState.AWAITING_REGISTER_READ) {
            prepareRead();
        } else if (state == JFrameBufferState.AWAITING_CLOSE) {
            close();
        } else {
            LOGGER.error("changeSelectInterest was called, but state is invalid ({})", state);
        }

        // 唤醒阻塞的Selector
        selectionKey.selector().wakeup();
    }

    /**
     * 服务端完成业务逻辑并将结果返回到客户端
     * */
    private void readyResponse(String msgId) throws IOException {
        // 服务端响应, 则更改FrameBuffer状态AWAITING_REGISTER_WRITE
        state = JFrameBufferState.AWAITING_REGISTER_WRITE;
        buffer = ByteBuffer.allocate(1024);
        serializer.encode(buffer, new MessageAck(msgId));
        buffer.flip();
        // 更改SelectionKey状态
        changeSelectInterests();
    }

    // SocketChannel数据读取完成后,处理业务
    public void invoke() {
        try {
            // 处理业务逻辑
            Object obj = serializer.decode(buffer.array());
            if (obj.getClass() == Message.class) {
                Message msg = (Message) obj;
                LOGGER.info("线程[{}]收到客户端消息: {}", Thread.currentThread().getName(), msg.toString());
                // 模拟输出数据
                readyResponse(msg.getMsgId());
            }
            return;
        } catch (Exception e) {
            LOGGER.warn("Exception while invoking!", e);
        } catch (Throwable t) {
            LOGGER.error("Unexpected throwable while invoking!", t);
        }

        // 发生异常, FrameBuffer状态更改为AWAITING_CLOSE
        state = JFrameBufferState.AWAITING_CLOSE;
        changeSelectInterests();
    }

    private enum JFrameBufferState {
        // 读Frame消息头,实际是4字节表示Frame长度(解决Socket粘包/拆包问题)
        READING_FRAME_SIZE,
        // 读Frame消息体
        READING_FRAME,
        // 读满包(Socket数据包读取完成)
        READ_FRAME_COMPLETE,

        // 等待注册写
        AWAITING_REGISTER_WRITE,
        // 写半包
        WRITING,
        // 等待注册读
        AWAITING_REGISTER_READ,
        // 等待关闭
        AWAITING_CLOSE
    }
}
