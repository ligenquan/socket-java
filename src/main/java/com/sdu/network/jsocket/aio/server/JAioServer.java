package com.sdu.network.jsocket.aio.server;

import com.sdu.network.jsocket.aio.callback.JAioAcceptHandler;
import com.sdu.network.jsocket.utils.JSocketUtils;
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
        ThreadFactory threadFactory = JSocketUtils.buildThreadFactory("aio-io-event-thread-%d", false);
        AsynchronousChannelGroup asyncChannelGroup = AsynchronousChannelGroup.withFixedThreadPool(args.getIoThreads(), threadFactory);
        asyncServerChannel = AsynchronousServerSocketChannel.open(asyncChannelGroup);

        // Note:
        //  1: AsynchronousServerSocketChannel在监听端口前设置:SO_RCVBUF
        //     在监听端口生成的AsynchronousSocketChannel继承AsynchronousServerSocketChannel的设置
        //  2: AsynchronousServerSocketChannel可设置Socket选项
        //     SO_RCVBUF/SO_REUSEADDR
        asyncServerChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        asyncServerChannel.setOption(StandardSocketOptions.SO_RCVBUF, 1024);

        // AsynchronousServerSocketChannel.open()并没有绑定监听地址
        // 若AsynchronousServerSocketChannel没有绑定监听地址, 调用accept()抛出异常: NotYetBoundException
        asyncServerChannel.bind(args.getBindAddress(), args.getBacklog());

        //
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
        serverArgs.setBindAddress(new InetSocketAddress(JSocketUtils.getIpV4(), 6712));

        //
        JAioServer server = new JAioServer(serverArgs);
        server.start();
    }
}
