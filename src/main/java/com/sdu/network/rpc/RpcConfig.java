package com.sdu.network.rpc;

import lombok.Builder;
import lombok.Getter;

/**
 * @author hanhan.zhang
 * */
@Getter
@Builder
public class RpcConfig {

    /**
     * RpcEnv Server绑定的地址
     * */
    private String host;

    /**
     * Rpc事件分发线程数
     * */
    private int dispatcherThreads;

}
