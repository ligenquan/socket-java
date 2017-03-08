package com.sdu.network.jsocket.aio.client;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author hanhan.zhang
 * */
public class JAioClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(JAioClient.class);

    private JClientArgs args;

    private AsynchronousSocketChannel asyncSocketChannel;

    private static ScheduledExecutorService scheduledExecutorService;

    public JAioClient(JClientArgs args) {
        this.args = args;
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
        asyncSocketChannel.connect(args.getRemoteAddress(), null, new CompletionHandler<Void, Void>() {
            @Override
            public void completed(Void result, Void attachment) {
                args.channelHandler.fireConnectComplete(asyncSocketChannel);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                LOGGER.error("occur connect exception in {} thread", Thread.currentThread().getName(), exc);
            }
        });

        // 读回调函数
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        asyncSocketChannel.read(buffer, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                args.getChannelHandler().fireReadComplete(asyncSocketChannel, result, buffer);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                LOGGER.info("io thread = {}, remote address closed", Thread.currentThread().getName());
                try {
                    asyncSocketChannel.close();
                } catch (IOException e) {
                    args.getChannelHandler().occurException(asyncSocketChannel, e);
                }
            }
        });

        group.awaitTermination(1, TimeUnit.MINUTES);
    }

    @Setter
    @Getter
    public static final class JClientArgs {

        // 远端服务地址
        private InetSocketAddress remoteAddress;

        // IO Event线程数
        private int ioThreads;

        private JAioChannelHandler channelHandler;
    }

    private static class DefaultAioChannelHandler implements JAioChannelHandler {

        @Override
        public void fireConnectComplete(AsynchronousSocketChannel asyncSocketChannel) {
            try {
                if (asyncSocketChannel.isOpen()) {
                    InetSocketAddress remoteAddress = (InetSocketAddress) asyncSocketChannel.getRemoteAddress();
                    String address = remoteAddress.getHostString() + ":" + remoteAddress.getPort();
                    LOGGER.info("io thread = {}, remote address = {}, connect success", Thread.currentThread().getName(), address);
                    ByteBuffer buffer = ByteBuffer.allocate(1024);

                    // 定时向服务器端发送消息
                    scheduledExecutorService.scheduleAtFixedRate(() -> {
                        buffer.clear();
                        buffer.put("heart beat".getBytes());
                        asyncSocketChannel.write(buffer, null, new CompletionHandler<Integer, Void>() {
                            @Override
                            public void completed(Integer result, Void attachment) {
                                fireWriteComplete(asyncSocketChannel, result, buffer);
                            }

                            @Override
                            public void failed(Throwable exc, Void attachment) {
                                LOGGER.info("io thread = {}, remote address = {}, closed", Thread.currentThread().getName(), remoteAddress);
                                try {
                                    asyncSocketChannel.close();
                                } catch (IOException e) {
                                    occurException(asyncSocketChannel, e);
                                }
                            }
                        });
                    }, 1, 1, TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                occurException(asyncSocketChannel, e);
            }
        }

        @Override
        public void fireAcceptComplete(AsynchronousSocketChannel asyncSocketChannel) {
            throw new UnsupportedOperationException("not support accept exception");
        }

        @Override
        public void fireReadComplete(AsynchronousSocketChannel asyncSocketChannel, int readSize, ByteBuffer buffer) {
            buffer.flip();
            String content = new String(buffer.array());
            buffer.clear();
            LOGGER.info("io thread = {}, size = {}, read content = {}", Thread.currentThread().getName(), readSize, content);
        }

        @Override
        public void fireWriteComplete(AsynchronousSocketChannel asyncSocketChannel, int writeSize, ByteBuffer buffer) {
            LOGGER.info("id thread = {}, write byte size = {}", Thread.currentThread().getName(), writeSize);
        }

        @Override
        public void occurException(AsynchronousSocketChannel asyncSocketChannel, Throwable t) {

        }
    }

    public static void main(String[] args) throws Exception {
        JClientArgs clientArgs = new JClientArgs();
        clientArgs.setIoThreads(5);
        clientArgs.setRemoteAddress(new InetSocketAddress(JAioUtils.getIpV4(), 6712));
        clientArgs.setChannelHandler(new DefaultAioChannelHandler());

        //
        JAioClient client = new JAioClient(clientArgs);
        client.start();
    }
}
