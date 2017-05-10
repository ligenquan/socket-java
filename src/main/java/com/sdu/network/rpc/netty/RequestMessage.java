package com.sdu.network.rpc.netty;

import com.esotericsoftware.kryo.io.ByteBufferInputStream;
import com.esotericsoftware.kryo.io.ByteBufferOutputStream;
import com.sdu.network.rpc.RpcAddress;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.*;
import java.nio.ByteBuffer;

/**
 * @author hanhan.zhang
 * */
@AllArgsConstructor
@Getter
public class RequestMessage {
    /**
     * 消息发送地址
     * */
    private RpcAddress senderAddress;
    /**
     * 消息接收方
     * */
    private NettyRpcEndPointRef receiver;
    /**
     * 消息体
     * */
    private Object content;

    public ByteBuffer serialize() {
        ByteBufferOutputStream bos = new ByteBufferOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        try {
            ObjectOutputStream objOut = new ObjectOutputStream(out);
            writeRpcAddress(out, senderAddress);
            writeRpcAddress(out, receiver.address());
            out.writeUTF(receiver.name());
            // 发送消息体
            objOut.writeObject(content);
            close(objOut);
            return bos.getByteBuffer();
        } catch (IOException e) {
            throw new IllegalStateException("serialize error");
        } finally {
            close(out);
        }
    }

    public static RequestMessage deserialize(ByteBuffer buffer, NettyRpcEnv rpcEnv, TransportClient client) {
        ByteBufferInputStream bis = new ByteBufferInputStream(buffer);
        DataInputStream input = new DataInputStream(bis);
        try {
            ObjectInputStream objInput = new ObjectInputStream(input);
            RpcAddress senderAddress = readRpcAddress(input);
            RpcAddress receiverAddress = readRpcAddress(input);
            String name = input.readUTF();
            // 消息体
            Object content = objInput.readObject();
            close(objInput);
            NettyRpcEndPointRef endPointRef = new NettyRpcEndPointRef(name, receiverAddress, rpcEnv);
            endPointRef.setClient(client);
            return new RequestMessage(senderAddress, endPointRef, content);
        } catch (Exception e) {
            // ignore
            throw new IllegalStateException("deserialize error");
        } finally {
            close(input);
        }
    }

    private static void writeRpcAddress(DataOutputStream out, RpcAddress address) throws IOException {
        if (address == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeUTF(address.getHost());
            out.writeInt(address.getPort());
        }
    }

    private static RpcAddress readRpcAddress(DataInputStream input) throws IOException {
        boolean isNull = input.readBoolean();
        if (isNull) {
            return null;
        }
        String host = input.readUTF();
        int port = input.readInt();
        return new RpcAddress(host, port);
    }

    private static void close(OutputStream out) {
        if (out == null) {
            return;
        }
        try {
            out.close();
        } catch (IOException e) {
            // ignore
        }
    }

    private static void close(InputStream is) {
        if (is == null) {
            return;
        }
        try {
            is.close();
        } catch (IOException e) {
            // ignore
        }
    }
}
