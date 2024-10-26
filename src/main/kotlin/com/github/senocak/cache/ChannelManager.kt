package com.github.senocak.cache

import io.netty.channel.Channel
import io.netty.util.AttributeKey
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class ChannelManager {
    private val channels: MutableMap<String, Channel> = ConcurrentHashMap()
    private val CHANNEL_ID_ATTRIBUTE_KEY = "CHANNEL_ID:"

    fun add(id: String, channel: Channel): Channel {
        channel.attr(channelAttributeKey()).set(id)
        channels[id] = channel
        return channel
    }

    fun toList(): List<String> = channels.map { it.key }.toList()

    fun get(id: String): Channel? = channels[id]

    fun remove(id: String): Channel? = channels.remove(key = id)

    fun remove(channel: Channel): Channel? {
        val id: String? = getChannelIdAttributeKey(channel = channel)
        return when {
            id.isNullOrEmpty() -> null
            else -> channels.remove(id)
        }
    }

    fun channelAttributeKey(): AttributeKey<String> =
        if (!AttributeKey.exists(CHANNEL_ID_ATTRIBUTE_KEY))
            AttributeKey.newInstance(CHANNEL_ID_ATTRIBUTE_KEY)
        else
            AttributeKey.valueOf(CHANNEL_ID_ATTRIBUTE_KEY)

    fun getChannelIdAttributeKey(channel: Channel): String? =
        channel.attr(channelAttributeKey()).get()
}
