package com.github.senocak.netty

import com.github.senocak.logger
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import jakarta.annotation.PreDestroy
import org.slf4j.Logger
import org.springframework.stereotype.Component
import java.net.InetSocketAddress

@Component
class NettyServer(
    private val nettyChannelInitializer: NettyChannelInitializer,
    private val nettyProperties: NettyProperties
) {
    private val log: Logger by logger()
    private lateinit var channel: Channel

    @Throws(InterruptedException::class)
    fun start() {
        val nettyTcpPort = InetSocketAddress(nettyProperties.port)
        log.info("[NettyServer:start] start server. (bind: $nettyTcpPort)")
        channel = ServerBootstrap()
            .group(NioEventLoopGroup(nettyProperties.bossThread), NioEventLoopGroup(nettyProperties.workerThread))
            .childHandler(nettyChannelInitializer)
            .channel(NioServerSocketChannel::class.java)
            .bind(nettyTcpPort)
            .sync()
            .channel()
            .closeFuture()
            .sync()
            .channel()
    }

    @PreDestroy
    fun stop() {
        log.info("[NettyServer:stop] stopping server.")
        channel.close()
        channel.parent().close()
    }
}
