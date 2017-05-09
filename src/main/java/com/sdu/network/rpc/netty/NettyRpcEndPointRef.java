package com.sdu.network.rpc.netty;

import com.sdu.network.rpc.RpcAddress;
import com.sdu.network.rpc.RpcEndPointRef;

/**
 * @author hanhan.zhang
 * */
public class NettyRpcEndPointRef extends RpcEndPointRef {

    /**
     * 引用节点地址
     * */
    private RpcAddress address;

    /**
     * 所属RpcEnv
     * */
    private NettyRpcEnv rpcEnv;

    public NettyRpcEndPointRef(RpcAddress address, NettyRpcEnv rpcEnv) {
        this.address = address;
        this.rpcEnv = rpcEnv;
    }
}
