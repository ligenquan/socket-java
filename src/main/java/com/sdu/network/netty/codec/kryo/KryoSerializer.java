package com.sdu.network.netty.codec.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.pool.KryoPool;
import com.google.common.io.Closer;
import io.netty.buffer.ByteBuf;
import org.objenesis.strategy.StdInstantiatorStrategy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author hanhan.zhang
 * */
public class KryoSerializer {

    private Class<?> []registerClazz;

    private ThreadLocal<KryoSerializerPool> poolHolder = new ThreadLocal<KryoSerializerPool>(){
        @Override
        protected KryoSerializerPool initialValue() {
            return new KryoSerializerPool();
        }
    };

    private Closer closer = Closer.create();

    public KryoSerializer(Class<?> ... registerClazz) {
        this.registerClazz = registerClazz;
    }

    public Object decode(byte[] bytes) throws IOException {
        ByteArrayInputStream byteArrayInputStream;
        try {
            byteArrayInputStream = new ByteArrayInputStream(bytes);
            Kryo kryo = poolHolder.get().borrow();
            Input in = new Input(byteArrayInputStream);
            Object result = kryo.readClassAndObject(in);

            // 释放资源
            closer.register(in);
            closer.register(byteArrayInputStream);
            poolHolder.get().release(kryo);
            return result;
        } finally {
            closer.close();
        }
    }

    public void encode(final ByteBuf out, final Object message) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = null;
        try {
            byteArrayOutputStream = new ByteArrayOutputStream();
            // 序列化
            Kryo kryo = poolHolder.get().borrow();
            Output output = new Output(byteArrayOutputStream);
            kryo.writeClassAndObject(output, message);
            output.flush();

            // 资源释放
            closer.register(byteArrayOutputStream);
            closer.register(output);
            poolHolder.get().release(kryo);

            // 输出编码: 数据包头 + 数据包长度
            byte[] body = byteArrayOutputStream.toByteArray();
            int dataLength = body.length;
            out.writeInt(dataLength);
            out.writeBytes(body);
        } finally {
            closer.close();
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
