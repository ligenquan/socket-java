package com.sdu.network.rpc;

/**
 * Rpc节点
 *
 * @author hanhan.zhang
 * */
public abstract class RpcEndPoint {

    private RpcEnv rpcEnv;

    public RpcEndPoint(RpcEnv rpcEnv) {
        this.rpcEnv = rpcEnv;
    }

    /**
     * 返回当前节点的网络通信节点{@link RpcEndPointRef}
     * */
    public final RpcEndPointRef self() {
        assert rpcEnv != null;
        return rpcEnv.endPointRef(this);
    }

    public abstract void onStart();

    public abstract void onEnd();

    public abstract void onStop();

    public abstract void onConnect(RpcAddress rpcAddress);

    public abstract void onDisconnect(RpcAddress rpcAddress);
}
