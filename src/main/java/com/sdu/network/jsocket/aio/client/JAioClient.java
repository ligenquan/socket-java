package com.sdu.network.jsocket.aio.client;

import com.sdu.network.jsocket.aio.handle.JAioChannelHandler;
import com.sdu.network.jsocket.aio.server.JAioServer;
import com.sdu.network.jsocket.aio.utils.JAioUtils;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author hanhan.zhang
 * */
public class JAioClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(JAioClient.class);

    private JClientArgs args;

    private AsynchronousSocketChannel asyncSocketChannel;

    public JAioClient(JClientArgs args) {
        this.args = args;
    }

    public void start() throws IOException, InterruptedException {
        ThreadFactory threadFactory = JAioUtils.buildThreadFactory("io-event-thread-%d", false);
        AsynchronousChannelGroup group = AsynchronousChannelGroup.withFixedThreadPool(args.getIoThreads(), threadFactory);
        asyncSocketChannel = AsynchronousSocketChannel.open(group);
        asyncSocketChannel.connect(args.getRemoteAddress(), null, new CompletionHandler<Void, Void>() {
            @Override
            public void completed(Void result, Void attachment) {
                args.channelHandler.fireConnect(asyncSocketChannel);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                LOGGER.error("occur connect exception in {} thread", Thread.currentThread().getName(), exc);
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
        public void fireConnect(AsynchronousSocketChannel asyncSocketChannel) {

        }

        @Override
        public void fireAccept(AsynchronousSocketChannel asyncSocketChannel) {
            throw new UnsupportedOperationException("not support accept exception");
        }

        @Override
        public void fireRead(AsynchronousSocketChannel asyncSocketChannel, int readSize, ByteBuffer buffer) {

        }

        @Override
        public void fireWrite(AsynchronousSocketChannel asyncSocketChannel, int writeSize, ByteBuffer buffer) {

        }

        @Override
        public void occurException(AsynchronousSocketChannel asyncSocketChannel, Throwable t) {

        }
    }

    public static void main(String[] args) {

    }
}
