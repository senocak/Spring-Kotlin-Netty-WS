package com.github.senocak.netty.cache

import io.netty.channel.Channel
import io.netty.util.AttributeKey
import org.apache.commons.lang3.StringUtils
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

    fun get(id: String): Channel? = channels[id]
    fun remove(id: String): Channel? = channels.remove(key = id)

    fun remove(channel: Channel): Channel? {
        val id = getChannelIdAttributeKey(channel = channel)
        return when {
            StringUtils.isNotEmpty(id) -> channels.remove(id)
            else -> null
        }
    }

    fun channelAttributeKey(): AttributeKey<String> =
        if (!AttributeKey.exists(CHANNEL_ID_ATTRIBUTE_KEY))
            AttributeKey.newInstance(CHANNEL_ID_ATTRIBUTE_KEY)
        else
            AttributeKey.valueOf(CHANNEL_ID_ATTRIBUTE_KEY)

    private fun getChannelIdAttributeKey(channel: Channel): String =
        channel.attr(channelAttributeKey()).get()
}
