package com.github.senocak

import com.github.senocak.netty.Group
import com.github.senocak.netty.GroupProcessingException
import com.github.senocak.netty.MessageFrame
import com.github.senocak.netty.NettyServer
import com.github.senocak.netty.Register
import com.github.senocak.netty.RegisterProcessingException
import com.github.senocak.netty.ResponseEntity
import com.github.senocak.netty.SendMessage
import com.github.senocak.netty.WSController
import com.github.senocak.netty.WSControllerAdvice
import com.github.senocak.netty.WSExceptionHandler
import com.github.senocak.netty.WSMapping
import io.netty.channel.ChannelHandlerContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.SmartLifecycle
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

@SpringBootApplication
class SpringKotlinWsScreenshotApplication(
    private val dispatcher: WebSocketDispatcher,
    private val nettyServer: NettyServer
): SmartLifecycle {
    private var running: Boolean = false

    override fun start(): Unit = nettyServer.start().also { running = true }
    override fun stop(): Unit = nettyServer.stop().also { running = false }
    override fun isRunning(): Boolean = running

    @GetMapping
    fun isAppRunning(): Boolean = running

    @PostMapping
    fun send(@RequestBody req: SendMessage.Request): SendMessage.Response {
        println("send")
        val message = MessageFrame(channelType = req.channelType, destination = req.destination,
            message = req.message, data = req.data)
        dispatcher.dispatch(messageFrame = message)
        return SendMessage.Response(destination = req.destination, sentAt = LocalDateTime.now())
    }
}

@WSController
class NettyWebSocketController(
    private val dispatcher: WebSocketDispatcher
){
    private val log: Logger by logger()

    @WSMapping(value = "register")
    fun register(req: Register.Request, ctx: ChannelHandlerContext, future: CompletableFuture<ResponseEntity?>) {
        log.info("register")
        val channel = dispatcher.join(name = req.token, channel = ctx.channel())
        if (channel == null)
            future.completeExceptionally(RegisterProcessingException(message = "user register failure."))
        val res = Register.Response(registeredAt = LocalDateTime.now(), message =  "Hi!")
        future.complete(ResponseEntity(identifier = req.token, message = "agent: " + req.agent, body = res))
    }

    @WSMapping(value = "unregister")
    fun unregister(ctx: ChannelHandlerContext, future: CompletableFuture<ResponseEntity?>) {
        log.info("unregister")
        dispatcher.leave(channel = ctx.channel())
        future.complete(ResponseEntity(status = ResponseEntity.ResponseStatus.OK))
    }

    @WSMapping(value = "join")
    fun join(req: Group.Request, ctx: ChannelHandlerContext, future: CompletableFuture<ResponseEntity>) {
        log.info("join")
        val joinSuccessful = dispatcher.joinGroup(name = req.groupName, channel = ctx.channel())
        if (!joinSuccessful)
            future.completeExceptionally(GroupProcessingException(message = "join to group failure."))
        val res = Group.Response(joinedAt = LocalDateTime.now(), message = "Hi!")
        future.complete(ResponseEntity(identifier = req.groupName, message = "Done", body = res))
    }

    @WSMapping(value = "leave")
    fun leave(req: Group.Request, ctx: ChannelHandlerContext, future: CompletableFuture<ResponseEntity>) {
        log.info("leave")
        val leaveSuccessful = dispatcher.leaveGroup(name = req.groupName, channel = ctx.channel())
        if (!leaveSuccessful)
            future.completeExceptionally(GroupProcessingException(message = "leave group failure."))
        val res = Group.Response(LocalDateTime.now(), "Bye!")
        future.complete(ResponseEntity(identifier = req.groupName, message = "Done", body = res))
    }
}

@WSControllerAdvice
class NettyWebSocketControllerAdvice {
    private val log: Logger by logger()

    @WSExceptionHandler(throwables = [GroupProcessingException::class])
    fun groupProcessingExceptionHandler(e: GroupProcessingException): ResponseEntity {
        log.error("[GroupProcessingException] error: ${e.message}")
        return ResponseEntity(status = ResponseEntity.ResponseStatus.ERROR, message = e.message)
    }

    @WSExceptionHandler(throwables = [RegisterProcessingException::class])
    fun registerProcessingExceptionHandler(e: RegisterProcessingException): ResponseEntity {
        log.error("[RegisterProcessingException] error: ${e.message}")
        return ResponseEntity(status = ResponseEntity.ResponseStatus.ERROR, message = e.message)
    }
}

fun main(args: Array<String>) {
    runApplication<SpringKotlinWsScreenshotApplication>(*args)
}

fun <R : Any> R.logger(): Lazy<Logger> = lazy {
    LoggerFactory.getLogger((if (javaClass.kotlin.isCompanion) javaClass.enclosingClass else javaClass).name)
}
