package com.github.senocak

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.senocak.netty.cache.ChannelGroupManager
import com.github.senocak.netty.cache.ChannelManager
import com.github.senocak.netty.MessageFrame
import com.github.senocak.netty.ResponseEntity
import io.netty.channel.Channel
import io.netty.channel.group.ChannelGroup
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import org.slf4j.Logger
import org.springframework.stereotype.Component

@Component
class WebSocketDispatcher(
    private val channelGroupManager: ChannelGroupManager,
    private val channelManager: ChannelManager,
    private val objectMapper: ObjectMapper
) {
    private val log: Logger by logger()

    fun dispatch(messageFrame: MessageFrame) {
        when (messageFrame.channelType) {
            MessageFrame.ChannelType.GROUP -> group(frame = messageFrame)
            MessageFrame.ChannelType.SINGLE -> single(frame = messageFrame)
        }
    }

    private fun group(frame: MessageFrame) {
        try {
            val group: ChannelGroup? = channelGroupManager.getOrCreate(frame.destination)
            val response = ResponseEntity(identifier = frame.destination, message = frame.message, body = frame.data)
            group?.writeAndFlush(TextWebSocketFrame(objectMapper.writeValueAsString(response)))
        } catch (e: JsonProcessingException) {
            log.error("[WebSocketDispatcher:group] error: ${e.message}")
        }
    }

    private fun single(frame: MessageFrame) {
        try {
            val channel: Channel? = channelManager.get(frame.destination)
            val response = ResponseEntity(identifier = frame.destination, message = frame.message, body = frame.data)
            channel?.writeAndFlush(TextWebSocketFrame(objectMapper.writeValueAsString(response)))
        } catch (e: JsonProcessingException) {
            log.error("[WebSocketDispatcher:single] error: ${e.message}")
        }
    }

    fun join(name: String, channel: Channel): Channel = channelManager.add(id = name, channel = channel)
    fun leave(channel: Channel): Channel? = channelManager.remove(channel = channel)
    fun joinGroup(name: String, channel: Channel): Boolean =
        channelGroupManager.getOrCreate(name = name)?.add(element = channel) ?: false
    fun leaveGroup(name: String, channel: Channel): Boolean =
        channelGroupManager.removeChannelInGroup(name = name, channel = channel)
}