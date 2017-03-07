package com.sdu.network.jsocket.aio.utils;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ThreadFactory;

/**
 * @author hanhan.zhang
 * */
public class JAioUtils {

    public static String getIpV4() throws UnknownHostException {
        InetAddress address = InetAddress.getLocalHost();
        return address.getHostAddress();
    }

    public static ThreadFactory buildThreadFactory(String format, boolean daemon) {
        return new ThreadFactoryBuilder().setNameFormat(format).setDaemon(daemon).build();
    }

}
