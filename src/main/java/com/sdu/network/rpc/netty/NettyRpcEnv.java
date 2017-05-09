package com.sdu.network.rpc.netty;

import com.google.common.collect.Maps;
import com.sdu.network.rpc.*;
import lombok.AllArgsConstructor;

import java.util.Map;

/**
 * @author hanhan.zhang
 * */
public class NettyRpcEnv extends RpcEnv {
    private String host;
    /**
     * RpcEnv Server端, 负责网络数据传输
     * */
    private TransportServer server;
    /**
     * 消息路由分发
     * */
    private Dispatcher dispatcher;

    /**
     * 消息发送信箱[key = 待接收地址, value = 发送信箱]
     * */
    private Map<RpcAddress, Outbox> outboxMessages = Maps.newConcurrentMap();

    public NettyRpcEnv(RpcConfig rpcConfig) {
        this.host = rpcConfig.getHost();
        this.dispatcher = new Dispatcher(this, rpcConfig);
    }

    @Override
    public RpcAddress address() {
        return server != null ? new RpcAddress(host, server.getPort()) : null;
    }

    @Override
    public RpcEndPointRef endPointRef(RpcEndPoint endPoint) {
        return dispatcher.getRpcEndPointRef(endPoint);
    }

    @Override
    public RpcEndPointRef setRpcEndPointRef(String name, RpcEndPoint endPoint) {
        return dispatcher.registerRpcEndPoint(name, endPoint);
    }

    @Override
    public void awaitTermination() {
        dispatcher.awaitTermination();
    }

    @Override
    public void stop(RpcEndPoint endPoint) {

    }

    @Override
    public void shutdown() {

    }

    @AllArgsConstructor
    public static class RequestMessage {
        /**
         * 消息发送地址
         * */
        private RpcAddress senderAddress;
        /**
         * 消息接收方
         * */
        private RpcEndPointRef receiver;
    }
}
