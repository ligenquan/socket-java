package com.sdu.network.netty.leak;

import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;

/**
 * 内存对象引用计数
 *
 * @author hanhan.zhang
 * */
public class MemoryObjectReferenceCount extends AbstractReferenceCounted {


    private ObjectTracker tracker;

    public MemoryObjectReferenceCount(ObjectTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    protected void deallocate() {
        // 对象引用计数为0, 调用该方法
        tracker.release();
    }

    @Override
    public ReferenceCounted touch(Object hint) {
        return this;
    }
}
