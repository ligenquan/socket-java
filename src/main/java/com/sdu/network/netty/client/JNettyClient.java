package com.sdu.network.netty.client;

import com.sdu.network.netty.codec.KryoSerializerDecoder;
import com.sdu.network.netty.codec.KryoSerializerEncoder;
import com.sdu.network.netty.codec.kryo.KryoSerializer;
import com.sdu.network.netty.handler.JClientChannelHandler;
import com.sdu.network.netty.msg.JMessage;
import com.sdu.network.netty.utils.JNettyUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 *
 * @apiNote
 *
 *  1: {@link EventLoopGroup}应有多个Client连接共享, 而非每个Client都对应创建一个{@link EventLoopGroup}
 *
 * @author hanhan.zhang
 * */
public class JNettyClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(JNettyClient.class);

    private EventLoopGroup _workerGroup;

    private ChannelFuture _channelFuture;

    public void start(int workerThreadNum, boolean ePoll, String host, int port) {
        _workerGroup = JNettyUtils.createEventLoopGroup(ePoll, workerThreadNum, "netty-client-thread-%d");
        // Bootstrap是Client启动辅助类
        Bootstrap bootstrap = new Bootstrap();

        // 设置工作线程
        bootstrap.group(_workerGroup);
        // 设置SocketChannel
        bootstrap.channel(JNettyUtils.getClientChannelClass(ePoll));
        // 设置Socket选项
        bootstrap.option(ChannelOption.SO_LINGER, 1024)
                 .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                 .option(ChannelOption.TCP_NODELAY, true)
                 .option(ChannelOption.SO_KEEPALIVE, true);
        // 初始化ChannelHandler[线程不安全]
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
//                p.addLast(new LoggingHandler(LogLevel.DEBUG));
                // 注意顺序[心跳发送(服务端每5秒检测链路读超时, 则客户端需每2秒写入心跳)]
                p.addLast(new IdleStateHandler(0, 4, 0, TimeUnit.SECONDS));
//                p.addLast(new StringEncoder());
//                p.addLast(new StringDecoder());
                KryoSerializer serializer = new KryoSerializer(JMessage.class);
                p.addLast(new KryoSerializerEncoder(serializer));
                p.addLast(new KryoSerializerDecoder(serializer));
                p.addLast(new JClientChannelHandler());
            }
        });

        // 连接Server
        _channelFuture = bootstrap.connect(new InetSocketAddress(host, port));
        _channelFuture.addListener((Future<? super Void> future) -> {
            if (future.isSuccess()) {
                LOGGER.info("connect success !");
            } else {
                LOGGER.error("connect failure !");
            }
        });
        _channelFuture.syncUninterruptibly();

    }

    public void stop() {
        if (_channelFuture != null) {
            _channelFuture.channel().closeFuture().awaitUninterruptibly(10, TimeUnit.SECONDS);
            _channelFuture = null;
        }
        if (_workerGroup != null) {
            _workerGroup.shutdownGracefully(10, 10, TimeUnit.SECONDS);
            _workerGroup = null;
        }
    }

    public static void main(String[] args) throws Exception {
        String host = JNettyUtils.getIpV4();
        JNettyClient client = new JNettyClient();
        client.start(10, false, host, 6712);

        TimeUnit.MINUTES.sleep(30);
        client.stop();
    }
}
