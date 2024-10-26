package com.github.senocak

import java.time.LocalDateTime
import java.util.Date

object Group {
    data class Request(val groupName: String)
    data class Response(
        val joinedAt: Long = Date().time,
        val message: String
    )
}
object Register {
    data class Request (val username: String, val agent: String)
    data class Response (
        val registeredAt: Long = Date().time,
        val message: String,
        val online: List<String>? = null,
    )
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
        val channelType: WsRequestBody.ChannelType = WsRequestBody.ChannelType.SINGLE,
        val destination: String,
        val message: String,
        val data: Any
    )

    data class Response (
        val destination: String,
        val sentAt: LocalDateTime = LocalDateTime.now()
    )
}

data class WsRequestBody(
    val channelType: ChannelType = ChannelType.SINGLE,
    var to: String,
){
    var from: String? = null
    var content: String? = null
    var date: Long = Date().time

    enum class ChannelType {
        GROUP, SINGLE
    }
}

object Screenshot {
    data class Request(val url: String)
    data class Response(val byteArray: String)
}

class AlreadyChannelGroupException(message: String) : Exception(message)
class WebSocketProcessingException(message: String) : Exception(message)
class RegisterProcessingException(message: String) : Exception(message)
class SeleniumException(message: String) : Exception(message)
class GroupProcessingException(message: String) : Exception(message)