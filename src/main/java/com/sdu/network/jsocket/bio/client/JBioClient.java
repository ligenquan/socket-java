package com.sdu.network.jsocket.bio.client;

import com.sdu.network.bean.Message;
import com.sdu.network.bean.MessageAck;
import com.sdu.network.codec.JSocketDataDecoder;
import com.sdu.network.codec.JSocketDataEncoder;
import com.sdu.network.jsocket.bio.callback.JBioStreamHandler;
import com.sdu.network.jsocket.utils.JSocketUtils;
import com.sdu.network.serializer.KryoSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author hanhan.zhang
 * */
public class JBioClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(JBioClient.class);

    private Socket socket;

    private JBioStreamHandler handler;

    public JBioClient(JBioStreamHandler handler) {
        this.handler = handler;
    }

    public void start(String host, int port) throws IOException {
        socket = new Socket();

        // 设置Socket参数
        socket.setKeepAlive(true);
        socket.setTcpNoDelay(true);
        socket.setReuseAddress(true);
        socket.setReceiveBufferSize(1024);

        // 连接服务器
        socket.connect(new InetSocketAddress(host, port));

        if (socket.isConnected()) {
            LOGGER.info("客户端连接服务器{}:{}成功", host, port);
        }
    }

    public void write() throws IOException {
        handler.write(socket);
    }

    public void read() throws IOException {
        handler.read(socket);
    }

    public void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public static void main(String[] args) throws Exception {

        JBioStreamHandler streamHandler = new JBioStreamHandler() {

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            // 序列化及拆包粘包处理
            KryoSerializer serializer = new KryoSerializer(Message.class, MessageAck.class);
            JSocketDataDecoder decoder = new JSocketDataDecoder(serializer);
            JSocketDataEncoder encoder = new JSocketDataEncoder(serializer);

            @Override
            public void read(Socket socket) throws IOException {
                List<Object> response = decoder.decode(new DataInputStream(socket.getInputStream()));
                if (response != null && response.size() > 0) {
                    String serverAddress = JSocketUtils.getClientAddress((InetSocketAddress) socket.getRemoteSocketAddress());
                    response.forEach(object -> {
                        if (object.getClass() == MessageAck.class) {
                            MessageAck ack = (MessageAck) object;
                            LOGGER.info("收到服务器{}的消息{}确认", serverAddress, ack.getMsgId());
                        }
                    });
                }
            }

            @Override
            public void write(Socket socket) throws IOException {
                Message msg = new Message(UUID.randomUUID().toString(), LocalDateTime.now().format(formatter));
                DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                encoder.encode(msg, outputStream);
                outputStream.flush();
            }
        };

        JBioClient client = new JBioClient(streamHandler);
        client.start("127.0.0.1", 6712);

        // 写线程
        Thread writeThread = new Thread() {
            @Override
            public void run() {
                for (;;) {
                    try {
                        TimeUnit.SECONDS.sleep(1);
                        client.write();
                    } catch (Exception e) {

                    }

                }
            }
        };
        writeThread.start();
//        client.write();

        // 读线程
        Thread readThread = new Thread(){
            @Override
            public void run() {
                for (;;) {
                    try {
                        client.read();
                    } catch (IOException e) {

                    }
                }
            }
        };
        readThread.start();

    }

}
