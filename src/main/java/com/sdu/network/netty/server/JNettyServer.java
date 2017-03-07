package com.sdu.network.netty.server;

import com.sdu.network.netty.codec.KryoSerializerDecoder;
import com.sdu.network.netty.codec.KryoSerializerEncoder;
import com.sdu.network.netty.codec.kryo.KryoSerializer;
import com.sdu.network.netty.handler.JServerChannelHandler;
import com.sdu.network.netty.msg.JMessage;
import com.sdu.network.netty.utils.JNettyUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 服务器端采用'Reactor'模型
 *
 *
 * @author hanhan.zhang
 * */
public class JNettyServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JNettyServer.class);

    /**
     * {@link Channel}关键API:
     *  1: {@link Channel#unsafe()}
     *     {@link Channel.Unsafe}用于实现Java Socket读/写/连接
     *
     *  2: {@link Channel#eventLoop()}
     *     负责处理Channel数据通信的线程池
     *
     *  3: {@link Channel#pipeline()}
     *     Channel传输数据处理器
     *
     * */
    private ChannelFuture[] _channelFutures;

    /**
     * _boosGroup负责监听客户端的连接请求:
     *  1: {@link NioServerSocketChannel}监听{@link SelectionKey#OP_ACCEPT}事件
     *     {@link NioServerSocketChannel#doReadMessages(List)}处理客户端连接
     *
     *  2: {@link NioServerSocketChannel#pipeline()}只添加一个{@link ChannelHandler}:
     *     {@link ServerBootstrap.ServerBootstrapAcceptor}
     *
     * Note: 若是监听一个端口, 则_boosGroup的线程设置为1, 若是监听n个端口则线程数设置为n
     * */
    private EventLoopGroup _bossGroup;

    /**
     * _workerGroup负责与客户端进行数据通信:
     *  1: {@link ServerBootstrap.ServerBootstrapAcceptor#channelRead(ChannelHandlerContext, Object)}
     *     完成SocketChannel事件绑定
     * */
    private EventLoopGroup _workerGroup;

    /**
     * @param backlog : Socket连接队列长度
     * @param workThreadNum : 工作线程
     * @param ports : 绑定端口
     * */
    public boolean start(int backlog, int workThreadNum, boolean ePoll, int ... ports) {
        try {
            int bossThreadNum = ports.length;
            _bossGroup = JNettyUtils.createEventLoopGroup(ePoll, bossThreadNum, "netty-server-boss-thread-%d");

            _workerGroup = JNettyUtils.createEventLoopGroup(ePoll, workThreadNum, "netty-server-worker-thread-%d");

            // ServerBootstrap是Server启动辅助类
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(_bossGroup, _workerGroup)
                     .channel(JNettyUtils.getServerChannelClass(ePoll));

            // 设置ServerSocket的连接队列
            if (backlog > 0) {
                bootstrap.option(ChannelOption.SO_BACKLOG, backlog);
                bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
            }

            // Channel读写缓冲区[Netty Buffer内存池]
            bootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                     .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);

            // 初始化ChannelHandler[线程不安全]
            bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ChannelPipeline p = ch.pipeline();
                    // 注意顺序[1:日志 2:心跳检测(每5秒检测服务端读超时)]
                    // 1: Netty日志
                    // 2: 心跳检测[每5秒检测服务端读超时]
//                    p.addLast(new LoggingHandler(LogLevel.INFO));
                    p.addLast(new IdleStateHandler(10, 0, 0, TimeUnit.SECONDS));
                    // Netty粘包/拆包处理
                    // Note:
                    //  客户端与服务端TCP粘包/拆包处理需一致
//                    p.addLast(new StringEncoder());
//                    p.addLast(new StringDecoder());
                    KryoSerializer serializer = new KryoSerializer(JMessage.class);
                    p.addLast(new KryoSerializerEncoder(serializer));
                    p.addLast(new KryoSerializerDecoder(serializer));
                    p.addLast(new JServerChannelHandler());
                }
            });

            _channelFutures = new ChannelFuture[ports.length];
            String ip = JNettyUtils.getIpV4();
            for (int i = 0; i < ports.length; ++i) {
                ChannelFuture channelFuture = bootstrap.bind(new InetSocketAddress(ip, ports[i]));
                channelFuture.syncUninterruptibly();
                _channelFutures[i] = channelFuture;
                int port = ((InetSocketAddress) channelFuture.channel().localAddress()).getPort();
                LOGGER.info("netty server start to listen {} port .", port);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void stop() {
        if (_channelFutures != null) {
            for (ChannelFuture future : _channelFutures) {
                if (future != null) {
                    future.channel().closeFuture().awaitUninterruptibly(10, TimeUnit.SECONDS);
                }
            }
            _channelFutures = null;
        }
        if (_bossGroup != null) {
            _bossGroup.shutdownGracefully(10, 10, TimeUnit.SECONDS);
            _bossGroup = null;
        }

        if (_workerGroup != null) {
            _workerGroup.shutdownGracefully(10, 10, TimeUnit.SECONDS);
            _workerGroup = null;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        JNettyServer server = new JNettyServer();
        server.start(50, 50, false, 6712, 6713);

        TimeUnit.MINUTES.sleep(50);
        server.stop();
    }
}
