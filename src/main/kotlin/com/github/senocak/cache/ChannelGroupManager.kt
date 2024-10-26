package com.github.senocak.cache

import com.github.senocak.logger
import com.github.senocak.AlreadyChannelGroupException
import io.netty.channel.Channel
import io.netty.channel.group.ChannelGroup
import io.netty.channel.group.DefaultChannelGroup
import io.netty.util.concurrent.ImmediateEventExecutor
import org.slf4j.Logger
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class ChannelGroupManager {
    private val log: Logger by logger()
    private val channelGroups: MutableMap<String, ChannelGroup> = ConcurrentHashMap()

    fun get(name: String): ChannelGroup? = channelGroups[name]

    @Throws(AlreadyChannelGroupException::class)
    fun create(name: String): ChannelGroup? {
        if (get(name = name) != null)
            throw AlreadyChannelGroupException("$name channel group already exists.")
        val group: ChannelGroup = DefaultChannelGroup(ImmediateEventExecutor.INSTANCE)
        return channelGroups.put(key = name, value = group)
    }

    fun getOrCreate(name: String): ChannelGroup? {
        var group = get(name = name)
        if (group == null) {
            try {
                group = create(name = name)
            } catch (e: AlreadyChannelGroupException) {
                log.warn("AlreadyChannelGroupException: ${e.message}")
                return get(name = name)
            }
        }
        return group
    }

    fun removeChannelInGroup(name: String, channel: Channel): Boolean {
        val group = get(name = name)
        return when {
            group != null -> group.remove(element = channel)
            else -> false
        }
    }

    fun removeChannelInAllGroups(channel: Channel) {
        channelGroups.forEach { (name: String, group: ChannelGroup) ->
            removeChannelInGroup(name = name, channel = channel) }
    }
}