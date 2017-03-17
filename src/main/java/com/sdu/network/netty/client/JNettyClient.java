package com.sdu.network.netty.client;

import com.sdu.network.bean.HeatBeat;
import com.sdu.network.bean.Message;
import com.sdu.network.bean.MessageAck;
import com.sdu.network.netty.codec.KryoSerializerDecoder;
import com.sdu.network.netty.codec.KryoSerializerEncoder;
import com.sdu.network.serializer.KryoSerializer;
import com.sdu.network.netty.handler.JClientChannelHandler;
import com.sdu.network.netty.utils.JNettyUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
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

    private EventLoopGroup workerGroup;

    private ChannelFuture channelFuture;

    public void start(int workerThreadNum, boolean ePoll, String host, int port) {
        // EventLooGroup负责管理EventLoop
        workerGroup = JNettyUtils.createEventLoopGroup(ePoll, workerThreadNum, "netty-client-thread-%d");

        // Bootstrap是Client启动辅助类
        Bootstrap bootstrap = new Bootstrap();

        // 设置工作线程
        bootstrap.group(workerGroup);
        // 设置SocketChannel
        bootstrap.channel(JNettyUtils.getClientChannelClass(ePoll));
        // 设置Socket选项
        bootstrap.option(ChannelOption.SO_LINGER, 1024)
                 .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                 .option(ChannelOption.TCP_NODELAY, true)
                 .option(ChannelOption.SO_KEEPALIVE, true);

        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
//                p.addLast(new LoggingHandler(LogLevel.DEBUG));
                // 注意顺序[心跳发送(服务端每5秒检测链路读超时, 则客户端需每2秒写入心跳)]
                p.addLast(new IdleStateHandler(0, 4, 0, TimeUnit.SECONDS));
//                p.addLast(new StringEncoder());
//                p.addLast(new StringDecoder());
                KryoSerializer serializer = new KryoSerializer(Message.class, MessageAck.class, HeatBeat.class);
                p.addLast(new KryoSerializerEncoder(serializer));
                p.addLast(new KryoSerializerDecoder(serializer));
                p.addLast(new JClientChannelHandler());
            }
        });

        // 连接Server
        channelFuture = bootstrap.connect(new InetSocketAddress(host, port));
        channelFuture.addListener((Future<? super Void> future) -> {
            if (future.isSuccess()) {
                LOGGER.info("connect success !");
            } else {
                LOGGER.error("connect failure !");
            }
        });

        channelFuture.syncUninterruptibly();

    }

    public EventLoop eventLoop() {
        return channelFuture.channel().eventLoop();
    }

    public ChannelFuture writeAndFlush(Object msg) {
        return channelFuture.channel().writeAndFlush(msg);
    }

    public void stop() {
        if (channelFuture != null) {
            channelFuture.channel().closeFuture().awaitUninterruptibly(10, TimeUnit.SECONDS);
            channelFuture = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(10, 10, TimeUnit.SECONDS);
            workerGroup = null;
        }
    }

    public static void main(String[] args) throws Exception {
        String host = JNettyUtils.getIpV4();
        JNettyClient client = new JNettyClient();
        client.start(10, false, host, 6712);


        // 定时发送数据
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        client.eventLoop().scheduleAtFixedRate(() -> {
            Message msg = new Message(UUID.randomUUID().toString(), LocalDateTime.now().format(formatter));
            client.writeAndFlush(msg);
        }, 1, 1, TimeUnit.SECONDS);

        TimeUnit.MINUTES.sleep(30);
        client.stop();
    }
}
