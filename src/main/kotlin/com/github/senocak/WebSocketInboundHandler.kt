package com.github.senocak

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.senocak.cache.ChannelGroupManager
import com.github.senocak.cache.ChannelManager
import com.github.senocak.invoker.WebSocketAdviceInvoker
import com.github.senocak.invoker.WebSocketControllerInvoker
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import org.slf4j.Logger
import org.springframework.stereotype.Component
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletableFuture

@Component("webSocketInboundHandler")
@Sharable
class WebSocketInboundHandler(
    private val webSocketControllerInvoker: WebSocketControllerInvoker,
    private val webSocketAdviceInvoker: WebSocketAdviceInvoker,
    private val channelGroupManager: ChannelGroupManager,
    private val channelManager: ChannelManager,
    private val objectMapper: ObjectMapper
): SimpleChannelInboundHandler<TextWebSocketFrame>() {
    private val log: Logger by logger()

    @Throws(java.lang.Exception::class)
    override fun channelActive(ctx: ChannelHandlerContext) {
        log.info("channelActive")
        ctx.fireChannelActive()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        channelManager.remove(channel = ctx.channel())
        channelGroupManager.removeChannelInAllGroups(channel = ctx.channel())
    }

    @Throws(Exception::class)
    override fun channelRead0(ctx: ChannelHandlerContext, frame: TextWebSocketFrame) {
        val future = CompletableFuture<ResponseEntity?>()
        val req = objectMapper.readValue(frame.retain().text(), RequestEntity::class.java)
        if (req.mapper.isEmpty())
            future.completeExceptionally(WebSocketProcessingException(message = "invalid request mapper name."))
        webSocketControllerInvoker.invoke(req = req, ctx = ctx, future = future)
        future.exceptionally { t: Throwable? ->
            try {
                val except: Any? = webSocketAdviceInvoker.invoke(throwable = t)
                sendResponse(ctx.channel(), except as ResponseEntity)
            } catch (e: InvocationTargetException) {
                log.error("[WebSocketInboundHandler:channelRead0:InvocationTargetException] error: ${e.message}")
            } catch (e: IllegalAccessException) {
                log.error("[WebSocketInboundHandler:channelRead0:IllegalAccessException] error: ${e.message}")
            }
            null
        }
        future.thenAccept { response: ResponseEntity? -> sendResponse(ctx.channel(), response) }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        sendResponse(channel = ctx.channel(),
            response = ResponseEntity(status = ResponseEntity.ResponseStatus.ERROR, message = "server error.")
        )
    }

    private fun sendResponse(channel: Channel, response: ResponseEntity?) {
        try {
            channel.writeAndFlush(TextWebSocketFrame(objectMapper.writeValueAsString(response)))
        } catch (e: JsonProcessingException) {
            log.error("[WebSocketInboundHandler:sendResponse:JsonProcessingException] error: ${e.message}")
        }
    }
}
