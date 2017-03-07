package com.sdu.network.jsocket.aio.server;

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

    public JAioServer(JServerArgs args) {
       this.args = args;
    }

    public void start() throws IOException, InterruptedException {
        //
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
                args.getChannelHandler().fireAccept(asyncSocketChannel);
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
        // 最大连接数
        private int backlog;

        // 绑定服务地址
        private InetSocketAddress bindAddress;

        // IO Event线程数
        private int ioThreads;

        private JAioChannelHandler channelHandler;
    }


    /**
     * AIO事件处理
     * */
    private static class DefaultAioChannelHandler implements JAioChannelHandler {
        @Override
        public void fireConnect(AsynchronousSocketChannel asyncSocketChannel) {
            throw new UnsupportedOperationException("not support connect operation");
        }

        @Override
        public void fireAccept(AsynchronousSocketChannel asyncSocketChannel) {
            try {
                if (asyncSocketChannel.isOpen()) {
                    asyncSocketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                    asyncSocketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                    asyncSocketChannel.setOption(StandardSocketOptions.SO_RCVBUF, 1024);
                    asyncSocketChannel.setOption(StandardSocketOptions.SO_SNDBUF, 1024);
                    // Socket操作前提是通道已被打开
                    InetSocketAddress remoteAddress = (InetSocketAddress) asyncSocketChannel.getRemoteAddress();
                    String address = remoteAddress.getHostString() + ":" + remoteAddress.getPort();
                    LOGGER.info("accept client {} connect, io thread : {}", address, Thread.currentThread().getName());

                    //
                    ByteBuffer readBuffer = ByteBuffer.allocate(1024);
                    asyncSocketChannel.read(readBuffer, null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer result, Void attachment) {
                            fireRead(asyncSocketChannel, result, readBuffer);
                        }

                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            LOGGER.error("occur read exception in {} thread", Thread.currentThread().getName(), exc);
                        }
                    });
                }
            } catch (Exception e) {
                occurException(asyncSocketChannel, e);
            }

        }

        @Override
        public void fireRead(AsynchronousSocketChannel asyncSocketChannel, int readSize, ByteBuffer buffer) {
            buffer.flip();
            LOGGER.info("io thread = {}, read size = {}, content = {}", Thread.currentThread().getName(), readSize, new String(buffer.array()));
            buffer.clear();
            asyncSocketChannel.write(buffer, null, new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(Integer result, Void attachment) {
                    fireWrite(asyncSocketChannel, result, buffer);
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    LOGGER.error("occur write exception in {} thread", Thread.currentThread().getName(), exc);
                }
            });
        }

        @Override
        public void fireWrite(AsynchronousSocketChannel asyncSocketChannel, int writeSize, ByteBuffer buffer) {

        }

        @Override
        public void occurException(AsynchronousSocketChannel asyncSocketChannel, Throwable t) {

        }
    }

    public static void main(String[] args) throws Exception {

        JServerArgs serverArgs = new JServerArgs();
        serverArgs.setIoThreads(Runtime.getRuntime().availableProcessors());
        serverArgs.setBacklog(100);
        serverArgs.setBindAddress(new InetSocketAddress(JAioUtils.getIpV4(), 6712));
        serverArgs.setChannelHandler(new DefaultAioChannelHandler());

        //
        JAioServer server = new JAioServer(serverArgs);
        server.start();
    }
}
