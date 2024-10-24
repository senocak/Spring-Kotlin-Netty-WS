package com.github.senocak.netty

import com.github.senocak.netty.MessageFrame.ChannelType
import java.time.LocalDateTime

object Group {
    data class Request(val groupName: String)
    data class Response(val joinedAt: LocalDateTime, val message: String)
}
object Register {
    data class Request (val token: String, val agent: String)
    data class Response (val registeredAt: LocalDateTime, val message: String)
}
data class RequestEntity(val mapper: String, val body: Any)

class ResponseEntity (
    val status: ResponseStatus = ResponseStatus.OK,
    val identifier: String? = null,
    val message: String? = null,
    val body: Any? = null
) {
    enum class ResponseStatus {
        OK, ERROR, BAD_REQUEST
    }
}

object SendMessage {
    data class Request (
        val channelType: ChannelType = ChannelType.SINGLE,
        val destination: String,
        val message: String,
        val data: Any
    )

    data class Response (
        val destination: String,
        val sentAt: LocalDateTime
    )
}

class MessageFrame(
    val channelType: ChannelType,
    val destination: String,
    val message: String,
    val data: Any
){
    enum class ChannelType {
        GROUP, SINGLE
    }
}

class AlreadyChannelGroupException(message: String) : Exception(message)
class WebSocketProcessingException(message: String) : Exception(message)
class RegisterProcessingException(message: String) : Exception(message)
class GroupProcessingException(message: String) : Exception(message)