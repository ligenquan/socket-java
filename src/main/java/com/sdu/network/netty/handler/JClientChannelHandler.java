package com.sdu.network.netty.handler;

import com.sdu.network.netty.msg.JMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;


/**
 * @author hanhan.zhang
 * */
public class JClientChannelHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JClientChannelHandler.class);

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 定时发送消息
        ctx.channel().eventLoop().scheduleAtFixedRate(()->
            ctx.writeAndFlush(new JMessage()) , 0, 3, TimeUnit.SECONDS);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();
        InetSocketAddress remote = (InetSocketAddress) ch.remoteAddress();
        LOGGER.info("receive {} from {}:{} at {} .", msg, remote.getHostName(), remote.getPort(), getCurrentTime());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.WRITER_IDLE) {
                // 写超时则需要写入心跳
                InetSocketAddress local = (InetSocketAddress) ctx.channel().localAddress();
                String clientAddress = local.getHostString() + ":" + local.getPort();
                ctx.writeAndFlush(new JMessage(clientAddress + " create heart beat at " + getCurrentTime()));
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

    private String getCurrentTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
}
