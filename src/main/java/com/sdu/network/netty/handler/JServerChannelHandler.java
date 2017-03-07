package com.sdu.network.netty.handler;

import com.google.common.collect.Maps;
import com.sdu.network.netty.msg.JMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * {@link io.netty.channel.ChannelHandler}线程不安全
 *
 * @author hanhan.zhang
 * */
public class JServerChannelHandler extends ChannelInboundHandlerAdapter{

    private static final Logger LOGGER = LoggerFactory.getLogger(JServerChannelHandler.class);

    private static final int DEFAULT_CLOSE_MISS_CONNECT = 3;

    /**
     * 客户端连接:
     *  Key = 客户端连接地址, Value = 失联次数
     * */
    private Map<InetSocketAddress, AtomicInteger> connectSocketMap = Maps.newConcurrentMap();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof JMessage) {
            JMessage message = (JMessage) msg;
            InetSocketAddress remote = (InetSocketAddress) ctx.channel().remoteAddress();
            LOGGER.info("receive {} from {} .", msg, remote.getHostString());
            ctx.writeAndFlush("process msg = " + message.getMsgId() + " success at " + getCurrentTime());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.error("Unexpected exception : {}", cause.getMessage(), cause);
        ctx.close();
    }

    /**
     * 客户端心跳处理
     * */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                // 读取超时
                InetSocketAddress remote = (InetSocketAddress) ctx.channel().remoteAddress();
                AtomicInteger loss = connectSocketMap.get(remote);
                if (loss == null) {
                    connectSocketMap.put(remote, new AtomicInteger(1));
                } else {
                    if (loss.get() + 1 >= DEFAULT_CLOSE_MISS_CONNECT) {
                        // 关闭客户端连接
                        ctx.channel().close();
                        connectSocketMap.remove(remote);
                    } else {
                        loss.incrementAndGet();
                    }
                }
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
}
