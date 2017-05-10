package com.sdu.network.utils;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author hanhan.zhang
 * */
public class ThreadUtils {

    private static ThreadFactory namedThreadFactory(String prefix, boolean daemon) {
        return new ThreadFactoryBuilder().setDaemon(daemon).setNameFormat(prefix).build();
    }

    public static ThreadPoolExecutor newDaemonCachedThreadPool(String prefix, int maxThreadNumber, int keepAliveSeconds) {
        ThreadFactory threadFactory = namedThreadFactory(prefix, true);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(maxThreadNumber, maxThreadNumber, keepAliveSeconds,
                TimeUnit.SECONDS, new LinkedBlockingQueue<>(), threadFactory);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

}
