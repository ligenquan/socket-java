package com.sdu.network.jsocket.nio.server.single;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
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
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单线程服务模型:
 *
 *  Server端单线线程用于处理客户端的连接请求
 *
 *  @apiNote
 *
 *     SelectionKey.OP_WRITE: 只要通道空闲,就触发写事件(容易造成CPU假虚高)
 *
 * @author hanhan.zhang
 * */
public class JNioOneThreadServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JNioOneThreadServer.class);

    // 监听通道事件
    private Selector selector;

    private ServerSocketChannel ssc;
    //
    private AtomicBoolean stopped = new AtomicBoolean(true);

    // Socket数据读取[半包处理]
    private JNioChannelHandler channelHandler;

    private Map<String, Set<String>> receivedMsg;

    private ByteBuffer readBuffer;

    public JNioOneThreadServer(JNioChannelHandler channelHandler, int readBufferSize) {
        this.channelHandler = channelHandler;
        this.readBuffer = ByteBuffer.allocate(readBufferSize);
        this.receivedMsg = Maps.newConcurrentMap();
    }

    public void serve(String host, int port) throws IOException {
        selector = Selector.open();

        ssc = ServerSocketChannel.open();
        // 必须设置为非阻塞
        ssc.configureBlocking(false);
        // 设置ServerSocket属性
        ssc.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        ssc.setOption(StandardSocketOptions.SO_RCVBUF, 1024);

        // 注册通道并监听OP_ACCEPT事件
        ssc.register(selector, SelectionKey.OP_ACCEPT);
        ssc.bind(new InetSocketAddress(host, port));
        if (ssc.isOpen()) {
            stopped.set(false);
        }

        // 处理客户端连接请求
        while (!stopped.get()) {
            selector.select();
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();

                // Note:
                //  接受的客户端连接SocketChannel, 也注册到selector中
                if(key.isAcceptable()) {
                    doAccept(key, selector);
                } else if (key.isReadable()) {
                    doRead(key);
                } else if (key.isWritable()) {
                    doWrite(key);
                }
            }
        }
    }

    private void doAccept(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
        SocketChannel sc = ssc.accept();
        if (!sc.isConnected()) {
            return;
        }
        sc.configureBlocking(false);
        sc.socket().setKeepAlive(true);
        sc.socket().setTcpNoDelay(true);
        // 注册通道
        sc.register(selector, SelectionKey.OP_READ);
    }

    private void doRead(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        int readSize = sc.read(readBuffer);
        List<Object> msgList = channelHandler.channelRead(sc, readSize, readBuffer);

        if (msgList != null && msgList.size() > 0) {
            String clientAddress = JSocketUtils.getClientAddress(sc);
            // 记录已接收消息
            Set<String> msgIsSet = receivedMsg.get(clientAddress);
            if (msgIsSet == null) {
                msgIsSet = Sets.newConcurrentHashSet();
                receivedMsg.put(clientAddress, msgIsSet);
            }
            for (Object object : msgList) {
                if (object.getClass() == Message.class) {
                    Message msg = (Message) object;
                    LOGGER.info("收到客户端{}的消息:{}", clientAddress, msg.toString());
                    msgIsSet.add(msg.getMsgId());
                }
            }

            // 读取客户端数据完成,响应客户端(注册写事件)
            preWrite(key);
        }
    }

    private void doWrite(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();

        String clientAddress = JSocketUtils.getClientAddress(sc);
        Set<String> msgIgSet = receivedMsg.get(clientAddress);
        ByteBuffer writeBuffer = ByteBuffer.allocate(1024);
        if (msgIgSet != null && msgIgSet.size() > 0) {
            Iterator<String> it = msgIgSet.iterator();
            while (it.hasNext()) {
                String msgId = it.next();
                it.remove();
                channelHandler.channelWrite(sc, new MessageAck(msgId), writeBuffer);
                sc.write(writeBuffer);
            }
        }

        // 响应客户端结束,改变通道监听事件为OP_READ(监听客户端再次发送的数据请求)
        preRead(key);
    }

    private void preWrite(SelectionKey key) {
        // 更改SelectionKey关联的SocketChannel关注事件，更改为OP_WRITE
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void preRead(SelectionKey key) {
        // 更改SelectionKey关联的SocketChannel关注事件，更改为OP_READ
        key.interestOps(SelectionKey.OP_READ);
    }

    public void close() throws IOException {
        stopped.set(true);
        selector.close();
        ssc.close();
    }

    public static void main(String[] args) throws IOException {
        KryoSerializer serializer = new KryoSerializer(Message.class, MessageAck.class);
        JSocketDataEncoder encoder = new JSocketDataEncoder(serializer);
        JSocketDataDecoder decoder = new JSocketDataDecoder(serializer);
        JDefaultNioChannelHandler channelHandler = new JDefaultNioChannelHandler(decoder, encoder);
        JNioOneThreadServer server = new JNioOneThreadServer(channelHandler, 1024);
        server.serve("127.0.0.1", 6721);
    }
}
