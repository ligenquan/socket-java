package com.sdu.network.netty.utils;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ThreadFactory;

/**
 *
 * Netty工具类
 *
 * @author hanhan.zhang
 * */
public class JNettyUtils {

    public static String getIpV4() throws UnknownHostException {
        InetAddress address = InetAddress.getLocalHost();
        return address.getHostAddress();
    }

    public static ThreadFactory buildThreadFactory(String format, boolean daemon) {
        return new ThreadFactoryBuilder().setNameFormat(format).setDaemon(daemon).build();
    }

    public static EventLoopGroup createEventLoopGroup(boolean ePoll, int threadNum, String format, boolean daemon) {
        if (ePoll) {
            return new EpollEventLoopGroup(threadNum, buildThreadFactory(format, daemon));
        }
        return new NioEventLoopGroup(threadNum, buildThreadFactory(format, daemon));
    }

    public static EventLoopGroup createEventLoopGroup(boolean ePoll, int threadNum, String format) {
        return createEventLoopGroup(ePoll, threadNum, format, false);
    }

    public static Class<? extends SocketChannel> getClientChannelClass(boolean ePoll) {
        if (ePoll) {
            return EpollSocketChannel.class;
        }
        return NioSocketChannel.class;
    }

    public static Class<? extends ServerChannel> getServerChannelClass(boolean ePoll) {
        if (ePoll) {
            return EpollServerSocketChannel.class;
        }
        return NioServerSocketChannel.class;
    }

}
