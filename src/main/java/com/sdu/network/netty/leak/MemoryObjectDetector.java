package com.sdu.network.netty.leak;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.ReferenceQueue;

/**
 * 内存对象检测工具
 *
 * @author hanhan.zhang
 * */
public class MemoryObjectDetector<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryObjectDetector.class);

    /**
     * 虚引用队列(虚引用被GC前会保存在队列中)
     * */
    private ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();


    public ObjectTracker open(T object) {
        leakDetect();
        /**
         * 新对象创建虚引用
         * */
        return new ObjectTracker(object, referenceQueue);
    }

    /**
     * 检测对象是否发生内存泄漏
     * */
    private void leakDetect() {
        for (;;) {
            ObjectTracker objectTracker = (ObjectTracker) referenceQueue.poll();
            if (objectTracker == null) {
                return;
            }

            if (objectTracker.release()) {
                LOGGER.error("内存泄漏");
            }
        }
    }
}