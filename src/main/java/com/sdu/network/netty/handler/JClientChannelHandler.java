package com.sdu.network.netty.handler;

import com.sdu.network.bean.HeatBeat;
import com.sdu.network.bean.MessageAck;
import com.sdu.network.jsocket.utils.JSocketUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


/**
 * @author hanhan.zhang
 * */
public class JClientChannelHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JClientChannelHandler.class);

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 定时发送消息
//        ctx.channel().eventLoop().scheduleAtFixedRate(()->
//            ctx.writeAndFlush(new Message(UUID.randomUUID().toString(), SDF.format(new Date()))) , 0, 3, TimeUnit.SECONDS);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg.getClass() == MessageAck.class) {
            MessageAck msgAck = (MessageAck) msg;
            Channel ch = ctx.channel();
            InetSocketAddress remote = (InetSocketAddress) ch.remoteAddress();
            LOGGER.info("接收到服务器{}的消息{}确认", JSocketUtils.getClientAddress(remote), msgAck.getMsgId());
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.WRITER_IDLE) {
                // 写超时则需要写入心跳
                InetSocketAddress local = (InetSocketAddress) ctx.channel().localAddress();
                String clientAddress = local.getHostString() + ":" + local.getPort();
                ctx.writeAndFlush(new HeatBeat(clientAddress, LocalDateTime.now().format(formatter)));
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.error("Unexpected exception : {}", cause.getMessage(), cause);
        ctx.close();
    }

}
