package com.sdu.network.rpc;

import lombok.AllArgsConstructor;

import java.net.URI;

/**
 * Rpc节点网络地址
 *
 * @author hanhan.zhang
 * */
@AllArgsConstructor
public class RpcAddress {

    private String host;

    private int port;

    public String hostPort() {
        return host + ":" + port;
    }

    public String toRpcURL() {
        return "rpc://" + hostPort();
    }

    public static RpcAddress fromURI(String rpcURL) {
        try {
            URI uri = new URI(rpcURL);
            String host = uri.getHost();
            int port = uri.getPort();
            return new RpcAddress(host, port);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid rpc url : " + rpcURL);
        }
    }
}
