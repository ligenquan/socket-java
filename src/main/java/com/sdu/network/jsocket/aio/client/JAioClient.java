package com.sdu.network.jsocket.aio.client;

import com.sdu.network.jsocket.aio.buf.JAioFrameBuffer;
import com.sdu.network.jsocket.aio.handle.JAioChannelHandler;
import com.sdu.network.jsocket.aio.utils.JAioUtils;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.*;

/**
 * @author hanhan.zhang
 * */
public class JAioClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(JAioClient.class);

    private JClientArgs args;

    private AsynchronousSocketChannel asyncSocketChannel;

    private JAioChannelHandler channelHandler;

    private ScheduledExecutorService scheduledExecutorService;

    public JAioClient(JClientArgs args) {
        this.args = args;
        channelHandler = new DefaultAioChannelHandler();
        ThreadFactory threadFactory = JAioUtils.buildThreadFactory("schedule-thread-%d", false);
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    public void start() throws IOException, InterruptedException {
        ThreadFactory threadFactory = JAioUtils.buildThreadFactory("io-event-thread-%d", false);
        AsynchronousChannelGroup group = AsynchronousChannelGroup.withFixedThreadPool(args.getIoThreads(), threadFactory);
        asyncSocketChannel = AsynchronousSocketChannel.open(group);
        asyncSocketChannel.setOption(StandardSocketOptions.SO_RCVBUF, 1024);
        asyncSocketChannel.setOption(StandardSocketOptions.SO_SNDBUF, 1024);
        asyncSocketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
        asyncSocketChannel.connect(args.getRemoteAddress(), asyncSocketChannel, new CompletionHandler<Void, AsynchronousSocketChannel>() {
            @Override
            public void completed(Void result, AsynchronousSocketChannel attachment) {
                channelHandler.fireConnectComplete(attachment);
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
                LOGGER.error("occur connect exception in {} thread", Thread.currentThread().getName(), exc);
            }
        });

        group.awaitTermination(1, TimeUnit.MINUTES);
    }

    @Setter
    @Getter
    public static final class JClientArgs {
        // 读缓冲区
        private int readBufferSize;
        // 远端服务地址
        private InetSocketAddress remoteAddress;
        // IO Event线程数
        private int ioThreads;
    }

    private class DefaultAioChannelHandler implements JAioChannelHandler {

        @Override
        public void fireConnectComplete(AsynchronousSocketChannel asyncSocketChannel) {
            if (!asyncSocketChannel.isOpen()) {
                return;
            }

//            // 注册读回调函数
//            ByteBuffer readBuffer = ByteBuffer.allocate(args.getReadBufferSize());
//            JAioFrameBuffer jAioFrameBuffer = new JAioFrameBuffer(asyncSocketChannel, readBuffer);
//            asyncSocketChannel.read(readBuffer, jAioFrameBuffer, new CompletionHandler<Integer, JAioFrameBuffer>() {
//                @Override
//                public void failed(Throwable exc, JAioFrameBuffer attachment) {
//                    try {
//                        attachment.closeChannel();
//                    } catch (IOException e) {
//                        channelHandler.occurException(attachment.getAsyncSocketChannel(), e);
//                    }
//                }
//
//                @Override
//                public void completed(Integer result, JAioFrameBuffer attachment) {
//                    fireReadComplete(asyncSocketChannel, result, jAioFrameBuffer);
//                }
//
//            });

            String msg = "heart beat";
            // 定时向服务器端发送消息
            scheduledExecutorService.scheduleAtFixedRate(() -> {
                ByteBuffer buffer = ByteBuffer.allocate(msg.length());
//                buffer.putInt(msg.length());
                buffer.put(msg.getBytes());
                buffer.flip();
                Future<Integer> future = asyncSocketChannel.write(buffer);
                try {
                    LOGGER.info("client write {} bytes", future.get());
                } catch (Exception e) {
                    // ignore
                    e.printStackTrace();
                }
            }, 1, 1, TimeUnit.SECONDS);
        }

        @Override
        public void fireReadComplete(AsynchronousSocketChannel asyncSocketChannel, int readSize, JAioFrameBuffer jAioFrameBuffer) {

            do {
                jAioFrameBuffer.preRead();
                // 读取一个数据包
                byte[] bytes = jAioFrameBuffer.read();
                if (bytes != null) {
                    LOGGER.info("io thread = {}, receive : {}", Thread.currentThread().getName(), new String(bytes));
                    jAioFrameBuffer.compactReadBuffer();
                }
            } while (jAioFrameBuffer.hasReadRemaining());

            jAioFrameBuffer.compactReadBuffer();
        }

        @Override
        public void fireAcceptComplete(AsynchronousSocketChannel asyncSocketChannel) {
            throw new UnsupportedOperationException("not support accept exception");
        }

        @Override
        public void fireWriteComplete(AsynchronousSocketChannel asyncSocketChannel, int writeSize, ByteBuffer buffer) {

        }

        @Override
        public void occurException(AsynchronousSocketChannel asyncSocketChannel, Throwable t) {

        }
    }

    public static void main(String[] args) throws Exception {
        JClientArgs clientArgs = new JClientArgs();
        clientArgs.setIoThreads(5);
        clientArgs.setReadBufferSize(1024);
        clientArgs.setRemoteAddress(new InetSocketAddress(JAioUtils.getIpV4(), 6712));

        //
        JAioClient client = new JAioClient(clientArgs);
        client.start();
    }
}
