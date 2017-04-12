package com.sdu.network.jsocket.bio.server;

import com.google.common.collect.Sets;
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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @author hanhan.zhang
 * */
public class JBioServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JBioServer.class);

    private ServerSocket serverSocket;

    private volatile boolean started = false;

    private JBioStreamHandler streamHandler;

    private Set<Task> threads;

    public JBioServer(JBioStreamHandler streamHandler) {
        this.streamHandler = streamHandler;
        threads = Sets.newHashSet();
    }

    public void start(String host, int port, int backlog) throws IOException {
        serverSocket = new ServerSocket();

        // 设置Socket参数
        serverSocket.setReuseAddress(true);
        serverSocket.setReceiveBufferSize(1024);

        // 绑定监听端口
        serverSocket.bind(new InetSocketAddress(host, port), backlog);

        if (serverSocket.isBound()) {
            LOGGER.info("服务端绑定地址{}:{}成功", host, port);
            started = true;
        }

        while (started) {
            Socket socket = serverSocket.accept();
            String clientAddress = JSocketUtils.getClientAddress((InetSocketAddress) socket.getRemoteSocketAddress());
            LOGGER.info("服务器接收来自客户端{}的连接请求", clientAddress);
            Task task = new Task(socket, "io-thread-" + threads.size());
            threads.add(task);
            task.start();
        }
    }

    public void close() throws IOException {
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        for (Task task : threads) {
            task.close();
        }
    }

    private class Task extends Thread {

        Socket socket;

        public Task(Socket socket, String name) {
            super(name);
            this.socket = socket;
        }

        @Override
        public void run() {
            for (;;) {
                try {
                    streamHandler.read(socket);
                } catch (IOException e) {
                    // socket关闭/socket尚未连接/socket流关闭
                    LOGGER.info("客户端{}关闭连接", JSocketUtils.getClientAddress((InetSocketAddress) socket.getRemoteSocketAddress()));
                    try {
                        socket.close();
                    } catch (IOException ex) {
                        // ignore
                    }
                    threads.remove(this);
                }
            }
        }

        public void close() throws IOException {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        JBioStreamHandler streamHandler = new JBioStreamHandler() {
            Set<String> receivedMsg = Sets.newHashSet();

            KryoSerializer serializer = new KryoSerializer(Message.class, MessageAck.class);
            JSocketDataDecoder decoder = new JSocketDataDecoder(serializer);
            JSocketDataEncoder encoder = new JSocketDataEncoder(serializer);

            @Override
            public void read(Socket socket) throws IOException {
                List<Object> msgList = decoder.decode(new DataInputStream(socket.getInputStream()));
                if (msgList == null || msgList.size() == 0) {
                    return;
                }
                msgList.forEach(object -> {
                    if (object.getClass() == Message.class) {
                        Message msg = (Message) object;
                        String clientAddress = JSocketUtils.getClientAddress((InetSocketAddress) socket.getRemoteSocketAddress());
                        LOGGER.info("接收到来自客户端{}的消息: {}", clientAddress, msg);
                        receivedMsg.add(msg.getMsgId());
                    }
                });
                write(socket);
            }

            @Override
            public void write(Socket socket) throws IOException {
                Iterator<String> iterator = receivedMsg.iterator();
                while (iterator.hasNext()) {
                    String msgId = iterator.next();
                    iterator.remove();
                    MessageAck messageAck = new MessageAck(msgId);
                    DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                    encoder.encode(messageAck, outputStream);
                    outputStream.flush();
                }
            }
        };

        JBioServer server = new JBioServer(streamHandler);
        server.start("127.0.0.1", 6712, 50);
    }

}
