package com.sdu.network.jsocket.aio.callback;

import com.sdu.network.jsocket.aio.utils.JAioUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.*;

/**
 * {@link JAioConnectHandler}职责:
 *
 *  1: 负责连接客户端
 *
 *  2: 注册读事件
 *
 * @author hanhan.zhang
 * */
public class JAioConnectHandler implements CompletionHandler<Void, AsynchronousSocketChannel> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JAioConnectHandler.class);

    private int readBufferSize;

    private ScheduledExecutorService scheduledExecutorService;

    public JAioConnectHandler(int readBufferSize) {
        this.readBufferSize = readBufferSize;
        ThreadFactory threadFactory = JAioUtils.buildThreadFactory("schedule-thread-%d", false);
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    @Override
    public void completed(Void result, AsynchronousSocketChannel attachment) {
        try {
            if (!attachment.isOpen()) {
                attachment.close();
                return;
            }

            String msg = "heart beat";
            scheduledExecutorService.scheduleAtFixedRate(() -> {
                ByteBuffer buffer = ByteBuffer.allocate(msg.length());
                buffer.put(msg.getBytes());
                buffer.flip();
                Future<Integer> future = attachment.write(buffer);
                try {
                    LOGGER.info("client write {} bytes", future.get());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 1, 1, TimeUnit.SECONDS);

            // 注册读回调函数
            ByteBuffer buffer = ByteBuffer.allocate(readBufferSize);
            attachment.read(buffer, buffer, new JAioReadHandler(attachment));
        } catch (IOException e) {
            LOGGER.error("connect exception", e);
        }
    }

    @Override
    public void failed(Throwable exc, AsynchronousSocketChannel attachment) {

    }

}
