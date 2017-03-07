package com.sdu.network.jsocket.nio.client;

import com.sdu.network.jsocket.nio.handle.JChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Test Client
 *
 * @author hanhan.zhang
 * */
public class JTestClient {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private static ByteBuffer decode(String msg) {
        int length = msg.length();
        ByteBuffer buffer = ByteBuffer.allocate(4 + length);
        buffer.putInt(length);
        buffer.put(msg.getBytes());
        buffer.flip();
        return buffer;
    }

    public static class SimpleChannelHandler implements JChannelHandler {

        private static final Logger LOGGER = LoggerFactory.getLogger(SimpleChannelHandler.class);

        @Override
        public void fireRead(SocketChannel sc) throws IOException{
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            int len = sc.read(buffer);
            if (len == -1) {
              occurException(new ClosedChannelException());
            } else if (len > 0) {
                buffer.flip();
                byte arr[] = new byte[buffer.remaining()];
                buffer.get(arr);
                LOGGER.info("client receive : {}", new String(arr));
                buffer.clear();
            }
        }

        @Override
        public void fireWrite(SocketChannel sc) throws IOException{
            String content = "client send heart beat at " + SDF.format(new Date());
            // 写数据
            try {
                sc.write(decode(content));
            } catch (Exception e) {
                occurException(e);
            }
        }

        @Override
        public void occurException(Exception e) {
            if (e instanceof ClosedChannelException) {
                LOGGER.error("remote socket channel closed exception", e);
            } else if (e instanceof IOException) {
                LOGGER.error("occur io exception", e);
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        JNioClient nioClient = new JNioClient(new SimpleChannelHandler());
        nioClient.start("127.0.0.1", 6721);
    }

}
