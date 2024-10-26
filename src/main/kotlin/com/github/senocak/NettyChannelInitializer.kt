package com.github.senocak

import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler
import org.springframework.stereotype.Component

@Component
class NettyChannelInitializer(
    private val webSocketInboundHandler: WebSocketInboundHandler,
    private val nettyProperties: NettyProperties
): ChannelInitializer<Channel>() {

    override fun initChannel(channel: Channel) {
        val pipeline = channel.pipeline()
        pipeline
            .addLast(HttpServerCodec())
            .addLast(HttpObjectAggregator(nettyProperties.maxContentLength))
            .addLast(WebSocketServerCompressionHandler())
            .addLast(WebSocketServerProtocolHandler(nettyProperties.socketPath, null, true))
            .addLast(webSocketInboundHandler)
    }
}
