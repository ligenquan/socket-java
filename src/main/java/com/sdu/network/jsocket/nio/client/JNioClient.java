package com.sdu.network.jsocket.nio.client;


import com.sdu.network.codec.JSocketDataDecoder;
import com.sdu.network.codec.JSocketDataEncoder;
import com.sdu.network.jsocket.aio.bean.Message;
import com.sdu.network.jsocket.aio.bean.MessageAck;
import com.sdu.network.jsocket.nio.callback.JNioChannelHandler;
import com.sdu.network.jsocket.nio.callback.impl.JDefaultNioChannelHandler;
import com.sdu.network.jsocket.utils.JSocketUtils;
import com.sdu.network.serializer.KryoSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * @author hanhan.zhang
 * */
public class JNioClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(JNioClient.class);

    private SocketChannel sc;

    private Selector selector;

    private AtomicBoolean started = new AtomicBoolean(true);

    private ByteBuffer readBuffer;

    private JNioChannelHandler channelHandler;

    public JNioClient(JNioChannelHandler handler, int readBufferSize) {
        this.channelHandler = handler;
        this.readBuffer = ByteBuffer.allocate(readBufferSize);
    }

    public void start(String host, int port) throws IOException {
        selector = Selector.open();
        // 设置SocketChannel
        sc = SocketChannel.open();
        sc.configureBlocking(false);
        // 注册SocketChannel感兴趣事件
        sc.register(selector, SelectionKey.OP_READ | SelectionKey.OP_CONNECT | SelectionKey.OP_WRITE);

        // 设置Socket选项
        sc.setOption(StandardSocketOptions.SO_SNDBUF, 1024);
        sc.setOption(StandardSocketOptions.SO_RCVBUF, 1024);
        sc.setOption(StandardSocketOptions.SO_KEEPALIVE, true);


        // 连接服务器
        sc.connect(new InetSocketAddress(host, port));
        while (started.get()) {
            selector.select();
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();
                if (!key.isValid()) {
                    continue;
                }
                if (key.isConnectable()) {
                    doConnect(key);
                } else if (key.isReadable()) {
                    doRead(key);
                } else if (key.isWritable()) {
                    doWrite(key);
                }
            }
        }
    }

    private void doConnect(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        if (sc.isConnectionPending()) {
            boolean finished = sc.finishConnect();
            if (finished) {
                started.set(true);
                // 更改SelectionKey的感兴趣事件, SelectionKey.OP_WRITE
                key.interestOps(SelectionKey.OP_WRITE);
            }
        }
    }

    private void doRead(SelectionKey key) throws IOException{
        SocketChannel sc = (SocketChannel) key.channel();
        int readSize = sc.read(readBuffer);
        List<Object> msgList = channelHandler.channelRead(sc, readSize, readBuffer);
        if (msgList != null && msgList.size() > 0) {
            String clientAddress = JSocketUtils.getClientAddress(sc);
            msgList.forEach(object -> {
                if (object.getClass() == MessageAck.class) {
                    MessageAck ack = (MessageAck) object;
                    LOGGER.info("收到服务器{}的消息{}确认", clientAddress, ack.getMsgId());
                }
            });
        }

        if (sc.isOpen()) {
            // 更改SelectionKey的感兴趣事件, SelectionKey.OP_WRITE
            key.interestOps(SelectionKey.OP_WRITE);
        } else {
            System.exit(1);
        }
    }

    private void doWrite(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        Message msg = new Message(UUID.randomUUID().toString(), new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        channelHandler.channelWrite(sc, msg, buffer);
        sc.write(buffer);
        // 更改SelectionKey的感兴趣事件, SelectionKey.OP_READ
        key.interestOps(SelectionKey.OP_READ);
    }

    public void stop() throws IOException {
        if (sc != null) {
            sc.close();
        }
        if (selector != null) {
            selector.close();
        }
        started.set(false);
    }


    public static void main(String[] args) throws IOException {
        KryoSerializer serializer = new KryoSerializer(Message.class, MessageAck.class);
        JSocketDataEncoder encoder = new JSocketDataEncoder(serializer);
        JSocketDataDecoder decoder = new JSocketDataDecoder(serializer);
        JDefaultNioChannelHandler channelHandler = new JDefaultNioChannelHandler(decoder, encoder);
        JNioClient client = new JNioClient(channelHandler, 1024);
        client.start("127.0.0.1", 6721);
    }
}
