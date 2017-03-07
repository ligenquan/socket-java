package com.sdu.network.netty.msg;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author hanhan.zhang
 * */
public class JMessage implements Serializable {

    private static final AtomicInteger taskCount = new AtomicInteger(1);

    private String msgId;

    private String msgContent;

    private String timestamp;

    public JMessage() {
        this("task number : " + taskCount.getAndIncrement());
    }

    public JMessage(String msg) {
        this.msgId = UUID.randomUUID().toString();
        msgContent = msg;
        timestamp = getCurrentTime();
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    public String getMsgId() {
        return msgId;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("msgId=").append(msgId).append("\t")
          .append("content=").append(msgContent).append("\t")
          .append("createTime=").append(timestamp);
        return sb.toString();
    }
}
