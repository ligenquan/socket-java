package com.sdu.network.netty.leak;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 追踪对象引用
 *
 * @author hanhan.zhang
 * */
public class ObjectTracker extends PhantomReference<Object> {
    /**
     * 对象是否已释放
     * */
    private AtomicBoolean freed;

    public ObjectTracker(Object referent, ReferenceQueue<? super Object> q) {
        super(referent, q);
        this.freed = new AtomicBoolean(false);
    }

    public boolean release() {
        return freed.compareAndSet(false, true);
    }
}
