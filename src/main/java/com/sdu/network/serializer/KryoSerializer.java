package com.sdu.network.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.pool.KryoPool;
import io.netty.buffer.ByteBuf;
import org.objenesis.strategy.StdInstantiatorStrategy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * @author hanhan.zhang
 * */
public class KryoSerializer {

    private Class<?> []registerClazz;

    private KryoSerializerPool kryoSerializerPool;

    public KryoSerializer(Class<?> ... registerClazz) {
        this.registerClazz = registerClazz;
        this.kryoSerializerPool = new KryoSerializerPool();
    }

    public Object decode(byte[] bytes) throws IOException {
        ByteArrayInputStream byteArrayInputStream = null;
        Input input = null;
        try {
            byteArrayInputStream = new ByteArrayInputStream(bytes);
            Kryo kryo = kryoSerializerPool.borrow();
            input = new Input(byteArrayInputStream);
            Object result = kryo.readClassAndObject(input);
            kryoSerializerPool.release(kryo);
            return result;
        } finally {
            close(byteArrayInputStream, input);
        }
    }

    public void encode(final ByteBuf out, final Object message) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = null;
        Output output = null;
        try {
            byteArrayOutputStream = new ByteArrayOutputStream();
            // 序列化
            Kryo kryo = kryoSerializerPool.borrow();
            output = new Output(byteArrayOutputStream);
            kryo.writeClassAndObject(output, message);
            output.flush();

            kryoSerializerPool.release(kryo);

            // 输出编码: 数据包头 + 数据包长度
            byte[] body = byteArrayOutputStream.toByteArray();
            int dataLength = body.length;
            out.writeInt(dataLength);
            out.writeBytes(body);
        } finally {
            close(byteArrayOutputStream, output);
        }
    }

    public void encode(final ByteBuffer buffer, final Object message) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = null;
        Output output = null;
        try {
            byteArrayOutputStream = new ByteArrayOutputStream();
            // 序列化
            Kryo kryo = kryoSerializerPool.borrow();
            output = new Output(byteArrayOutputStream);
            kryo.writeClassAndObject(output, message);
            output.flush();

            kryoSerializerPool.release(kryo);

            // 输出编码: 数据包头 + 数据包长度
            byte[] body = byteArrayOutputStream.toByteArray();
            int dataLength = body.length;
            buffer.putInt(dataLength);
            buffer.put(body);
        } finally {
            close(byteArrayOutputStream, output);
        }
    }

    public void encode(final OutputStream outputStream, final Object message) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = null;
        Output output = null;
        try {
            byteArrayOutputStream = new ByteArrayOutputStream();
            // 序列化
            Kryo kryo = kryoSerializerPool.borrow();
            output = new Output(byteArrayOutputStream);
            kryo.writeClassAndObject(output, message);
            output.flush();

            kryoSerializerPool.release(kryo);

            // 输出编码: 数据包头 + 数据包长度
            byte[] body = byteArrayOutputStream.toByteArray();
            int dataLength = body.length;
            outputStream.write(dataLength);
            outputStream.write(body);
        } finally {
            close(byteArrayOutputStream, output);
        }
    }

    private void close(ByteArrayInputStream inputStream, Input input) {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
            if (input != null) {
                input.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void close(ByteArrayOutputStream outputStream, Output output) {
        try {
            outputStream.close();
            output.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class KryoSerializerPool {

        private KryoPool kryoPool;

        KryoSerializerPool() {

            KryoFactory kryoFactory = new KryoFactory() {
                @Override
                public Kryo create() {
                    Kryo kryo = new Kryo();
                    kryo.setReferences(false);
                    if (registerClazz != null) {
                        for (Class c : registerClazz) {
                            kryo.register(c);
                        }
                    }
                    kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
                    return kryo;
                }
            };
            kryoPool = new KryoPool.Builder(kryoFactory).build();
        }

        public Kryo borrow() {
            assert kryoPool != null;
            return kryoPool.borrow();
        }

        public void release(Kryo kryo) {
            assert kryo != null && kryoPool != null;
            kryoPool.release(kryo);
        }
    }

}
