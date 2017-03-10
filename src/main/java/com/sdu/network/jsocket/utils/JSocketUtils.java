package com.sdu.network.jsocket.utils;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ThreadFactory;

/**
 * @author hanhan.zhang
 * */
public class JSocketUtils {

    public static String getIpV4() throws UnknownHostException {
        InetAddress address = InetAddress.getLocalHost();
        return address.getHostAddress();
    }

    public static ThreadFactory buildThreadFactory(String format, boolean daemon) {
        return new ThreadFactoryBuilder().setNameFormat(format).setDaemon(daemon).build();
    }

    public static String getClientAddress(SocketChannel socketChannel) throws IOException {
        InetSocketAddress socketAddress = ((InetSocketAddress)socketChannel.getRemoteAddress());
        return getClientAddress(socketAddress);
    }

    public static String getClientAddress(InetSocketAddress socketAddress) {
        return socketAddress.getHostString() + ":" + socketAddress.getPort();
    }
}
