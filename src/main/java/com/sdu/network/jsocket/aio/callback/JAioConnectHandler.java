package com.sdu.network.jsocket.aio.callback;

import com.sdu.network.bean.Message;
import com.sdu.network.codec.JSocketDataEncoder;
import com.sdu.network.jsocket.utils.JSocketUtils;
import com.sdu.network.serializer.KryoSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
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

    private KryoSerializer serializer;
    private JSocketDataEncoder encoder;

    public JAioConnectHandler(int readBufferSize, KryoSerializer serializer) {
        this.readBufferSize = readBufferSize;
        this.serializer = serializer;
        encoder = new JSocketDataEncoder(serializer);
        ThreadFactory threadFactory = JSocketUtils.buildThreadFactory("schedule-thread-%d", false);
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    @Override
    public void completed(Void result, AsynchronousSocketChannel attachment) {
        try {
            if (!attachment.isOpen()) {
                attachment.close();
                return;
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            scheduledExecutorService.scheduleAtFixedRate(() -> {
                if (!attachment.isOpen()) {
                    System.exit(1);
                    return;
                }
                Message msg = new Message(UUID.randomUUID().toString(), sdf.format(new Date()));
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                encoder.encode(msg, buffer);
                Future<Integer> future = attachment.write(buffer);
                try {
                    LOGGER.info("client write {} bytes", future.get());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 1, 1, TimeUnit.SECONDS);

            // 注册读回调函数
            ByteBuffer buffer = ByteBuffer.allocate(readBufferSize);
            attachment.read(buffer, buffer, new JAioClientReadHandler(attachment, this.serializer));
        } catch (IOException e) {
            LOGGER.error("connect exception", e);
        }
    }

    @Override
    public void failed(Throwable exc, AsynchronousSocketChannel attachment) {

    }

}
