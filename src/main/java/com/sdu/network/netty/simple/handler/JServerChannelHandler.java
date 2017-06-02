package com.sdu.network.netty.simple.handler;

import com.google.common.collect.Maps;
import com.sdu.network.bean.HeatBeat;
import com.sdu.network.bean.Message;
import com.sdu.network.bean.MessageAck;
import com.sdu.network.jsocket.utils.JSocketUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
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
        if (msg instanceof Message) {
            Message message = (Message) msg;
            InetSocketAddress remote = (InetSocketAddress) ctx.channel().remoteAddress();
            LOGGER.info("接收到客户端{}的消息{}", JSocketUtils.getClientAddress(remote), message.toString());
            // 响应客户端
            ctx.writeAndFlush(new MessageAck(message.getMsgId()));
        } else if (msg instanceof HeatBeat) {
            HeatBeat heatBeat = (HeatBeat) msg;
            LOGGER.info("接收到客户端{}在{}发送的心跳", heatBeat.getSenderAddress(), heatBeat.getSenderAddress());
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
}
