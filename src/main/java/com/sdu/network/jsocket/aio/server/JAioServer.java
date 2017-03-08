package com.sdu.network.jsocket.aio.server;

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
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author hanhan.zhang
 * */
public class JAioServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JAioServer.class);

    private JServerArgs args;

    private AsynchronousServerSocketChannel asyncServerChannel;

    private DefaultAioChannelHandler channelHandler;

    public JAioServer(JServerArgs args) {
        this.args = args;
        channelHandler = new DefaultAioChannelHandler();
    }

    public void start() throws IOException, InterruptedException {
        ThreadFactory threadFactory = JAioUtils.buildThreadFactory("aio-io-event-thread-%d", false);
        AsynchronousChannelGroup asyncChannelGroup = AsynchronousChannelGroup.withFixedThreadPool(args.getIoThreads(), threadFactory);
        asyncServerChannel = AsynchronousServerSocketChannel.open(asyncChannelGroup);
        asyncServerChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        asyncServerChannel.bind(args.getBindAddress(), args.getBacklog());
        asyncServerChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override
            public void completed(AsynchronousSocketChannel asyncSocketChannel, Void attachment) {
                // 继续接收连接
                asyncServerChannel.accept(null, this);
                // 在IO线程中处理[即在AsynchronousChannelGroup提供的线程池完成]
                channelHandler.fireAcceptComplete(asyncSocketChannel);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                LOGGER.error("occur accept exception in {} thread", Thread.currentThread().getName(), exc);
            }
        });

        asyncChannelGroup.awaitTermination(1, TimeUnit.MINUTES);
    }


    @Setter
    @Getter
    public static final class JServerArgs {
        // 读缓冲区
        private int readBufferSize;
        // 写缓冲区
        private int writeBufferSize;
        // 最大连接数
        private int backlog;
        // 绑定服务地址
        private InetSocketAddress bindAddress;
        // IO Event线程数
        private int ioThreads;
    }


    /**
     * AIO事件处理
     * */
    private class DefaultAioChannelHandler implements JAioChannelHandler {

        @Override
        public void fireConnectComplete(AsynchronousSocketChannel asyncSocketChannel) {
            throw new UnsupportedOperationException("not support connect operation");
        }

        @Override
        public void fireAcceptComplete(AsynchronousSocketChannel asyncSocketChannel) {
            if (!asyncSocketChannel.isOpen()) {
                return;
            }

            // Socket操作前提是通道已被打开
            try {
                // socket参数设置
                asyncSocketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                asyncSocketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                asyncSocketChannel.setOption(StandardSocketOptions.SO_RCVBUF, 1024);
                asyncSocketChannel.setOption(StandardSocketOptions.SO_SNDBUF, 1024);

                // 客户端连接信息
                InetSocketAddress remoteAddress = (InetSocketAddress) asyncSocketChannel.getRemoteAddress();
                String address = remoteAddress.getHostString() + ":" + remoteAddress.getPort();
                LOGGER.info("io thread = {}, client address = {}, accept connect", Thread.currentThread().getName(), address);

                // 注册读回调函数[AsynchronousSocketChannel对应一个ByteBuffer]
                ByteBuffer readBuffer = ByteBuffer.allocate(args.getReadBufferSize());
                JAioFrameBuffer aioFrameBuffer = new JAioFrameBuffer(asyncSocketChannel, readBuffer);
                asyncSocketChannel.read(readBuffer, aioFrameBuffer, new CompletionHandler<Integer, JAioFrameBuffer>() {
                    @Override
                    public void completed(Integer result, JAioFrameBuffer attachment) {
                        fireReadComplete(asyncSocketChannel, result, attachment);
                    }

                    @Override
                    public void failed(Throwable exc, JAioFrameBuffer attachment) {
                        try {
                            attachment.closeChannel();
                        } catch (IOException e) {
                            occurException(attachment.getAsyncSocketChannel(), e);
                        }
                    }
                });

            } catch (Exception e) {
                occurException(asyncSocketChannel, e);
            }

        }

        @Override
        public void fireReadComplete(AsynchronousSocketChannel asyncSocketChannel, int readSize, JAioFrameBuffer jAioFrameBuffer) {
            try {
                String threadName = Thread.currentThread().getName();
                InetSocketAddress remoteAddress = (InetSocketAddress) asyncSocketChannel.getRemoteAddress();
                String address = remoteAddress.getHostString() + ":" + remoteAddress.getPort();

                do {
                    byte[] bytes = jAioFrameBuffer.read();
                    if (bytes != null) {
                        LOGGER.info("io thread = {}, client = {}, content = {}", threadName, address, new String(bytes));
                        //
                        jAioFrameBuffer.write("OK");
                    }
                    jAioFrameBuffer.preRead();
                    jAioFrameBuffer.compactReadBuffer();
                } while (jAioFrameBuffer.hasReadRemaining());

            } catch (Exception e) {
                // ignore
            }
        }

        @Override
        public void fireWriteComplete(AsynchronousSocketChannel asyncSocketChannel, int writeSize, ByteBuffer buffer) {

        }

        @Override
        public void occurException(AsynchronousSocketChannel asyncSocketChannel, Throwable t) {

        }
    }

    public static void main(String[] args) throws Exception {

        JServerArgs serverArgs = new JServerArgs();
        serverArgs.setReadBufferSize(1024);
        serverArgs.setWriteBufferSize(1024);
        serverArgs.setIoThreads(Runtime.getRuntime().availableProcessors());
        serverArgs.setBacklog(100);
        serverArgs.setBindAddress(new InetSocketAddress(JAioUtils.getIpV4(), 6712));

        //
        JAioServer server = new JAioServer(serverArgs);
        server.start();
    }
}
