package com.sdu.network.jsocket.aio.client;

import com.sdu.network.bean.Message;
import com.sdu.network.bean.MessageAck;
import com.sdu.network.jsocket.aio.callback.JAioConnectHandler;
import com.sdu.network.jsocket.utils.JSocketUtils;
import com.sdu.network.serializer.KryoSerializer;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author hanhan.zhang
 * */
public class JAioClient {

    private JClientArgs args;

    private AsynchronousSocketChannel asyncSocketChannel;

    public JAioClient(JClientArgs args) {
        this.args = args;
    }

    public void start() throws IOException, InterruptedException {
        ThreadFactory threadFactory = JSocketUtils.buildThreadFactory("io-event-thread-%d", false);
        AsynchronousChannelGroup group = AsynchronousChannelGroup.withFixedThreadPool(args.getIoThreads(), threadFactory);
        asyncSocketChannel = AsynchronousSocketChannel.open(group);
        // Note:
        //  1: AsynchronousSocketChannel需在Connect前设置SO_RCVBUF/SO_SNDBUF
        //     TCP在三次牵手中需要告知Server的自己的发送窗口
        //  2: AsynchronousSocketChannel允许设置的Socket选项
        //     SO_SNDBUF/SO_RCVBUF/SO_KEEPALIVE/SO_REUSEADDR/TCP_NODELAY
        asyncSocketChannel.setOption(StandardSocketOptions.SO_RCVBUF, 1024);
        asyncSocketChannel.setOption(StandardSocketOptions.SO_SNDBUF, 1024);
        asyncSocketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);

        //
        asyncSocketChannel.connect(args.getRemoteAddress(), asyncSocketChannel, new JAioConnectHandler(args.getReadBufferSize(), new KryoSerializer(Message.class, MessageAck.class)));

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


    public static void main(String[] args) throws Exception {
        JClientArgs clientArgs = new JClientArgs();
        clientArgs.setIoThreads(5);
        clientArgs.setReadBufferSize(1024);
        clientArgs.setRemoteAddress(new InetSocketAddress(JSocketUtils.getIpV4(), 6712));

        //
        JAioClient client = new JAioClient(clientArgs);
        client.start();
    }
}
