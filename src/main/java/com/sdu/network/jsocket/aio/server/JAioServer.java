package com.sdu.network.jsocket.aio.server;

import com.sdu.network.jsocket.aio.callback.JAioAcceptHandler;
import com.sdu.network.jsocket.aio.utils.JAioUtils;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author hanhan.zhang
 * */
public class JAioServer {

    private JServerArgs args;

    private AsynchronousServerSocketChannel asyncServerChannel;


    public JAioServer(JServerArgs args) {
        this.args = args;
    }

    public void start() throws IOException, InterruptedException {
        ThreadFactory threadFactory = JAioUtils.buildThreadFactory("aio-io-event-thread-%d", false);
        AsynchronousChannelGroup asyncChannelGroup = AsynchronousChannelGroup.withFixedThreadPool(args.getIoThreads(), threadFactory);
        asyncServerChannel = AsynchronousServerSocketChannel.open(asyncChannelGroup);
        // Note:
        //  AsynchronousServerSocketChannel在监听端口前设置:SO_RCVBUF/SO_SNDBUF
        //  原因: 在监听端口生成的AsynchronousSocketChannel继承AsynchronousServerSocketChannel的设置
        asyncServerChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        asyncServerChannel.setOption(StandardSocketOptions.SO_RCVBUF, 1024);
        asyncServerChannel.setOption(StandardSocketOptions.SO_SNDBUF, 1024);
        asyncServerChannel.bind(args.getBindAddress(), args.getBacklog());
        asyncServerChannel.accept(asyncServerChannel, new JAioAcceptHandler(args.getReadBufferSize()));

        asyncChannelGroup.awaitTermination(1, TimeUnit.MINUTES);
    }


    @Setter
    @Getter
    public static final class JServerArgs {
        // 读缓冲区
        private int readBufferSize;
        // 最大连接数
        private int backlog;
        // 绑定服务地址
        private InetSocketAddress bindAddress;
        // IO Event线程数
        private int ioThreads;
    }

    public static void main(String[] args) throws Exception {

        JServerArgs serverArgs = new JServerArgs();
        serverArgs.setReadBufferSize(1024);
        serverArgs.setIoThreads(Runtime.getRuntime().availableProcessors());
        serverArgs.setBacklog(100);
        serverArgs.setBindAddress(new InetSocketAddress(JAioUtils.getIpV4(), 6712));

        //
        JAioServer server = new JAioServer(serverArgs);
        server.start();
    }
}
