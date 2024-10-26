package com.github.senocak

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.senocak.cache.ChannelGroupManager
import com.github.senocak.cache.ChannelManager
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.group.ChannelGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture

@SpringBootApplication
class SpringKotlinWsScreenshotApplication(
    private val nettyChannelInitializer: NettyChannelInitializer,
    private val nettyProperties: NettyProperties
): SmartLifecycle {
    private val log: Logger by logger()
    private var running: Boolean = false
    private lateinit var channel: Channel

    @Throws(InterruptedException::class)
    override fun start() {
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
    override fun stop() {
        log.info("[NettyServer:stop] stopping server.")
        channel.close()
        channel.parent().close()
    }
    override fun isRunning(): Boolean = running
}

@Component
@WSController
@WSControllerAdvice
class NettyWebSocketController(
    private val channelGroupManager: ChannelGroupManager,
    private val channelManager: ChannelManager,
    private val objectMapper: ObjectMapper
){
    private val log: Logger by logger()

    @WSMapping(value = "register")
    fun register(req: Register.Request, ctx: ChannelHandlerContext, future: CompletableFuture<ResponseEntity?>) {
        log.info("register")
        val channel: Channel = channelManager.add(id = req.username, channel = ctx.channel())
        if (channel == null) {
            future.completeExceptionally(RegisterProcessingException(message = "user register failure."))
            return
        }
        val res = Register.Response(message = "register", online = allChannelManagers())
        future.complete(ResponseEntity(identifier = req.username, message = "agent: " + req.agent, body = res))
    }

    @WSMapping(value = "unregister")
    fun unregister(req: Register.Request, ctx: ChannelHandlerContext, future: CompletableFuture<ResponseEntity?>) {
        log.info("unregister")
        val unregister: Channel? = channelManager.remove(channel = ctx.channel())
        if (unregister == null) {
            future.completeExceptionally(RegisterProcessingException(message = "user unregister failure."))
            return
        }
        val res = Register.Response(message = "unregister: ${unregister.id()}", online = allChannelManagers())
        future.complete(ResponseEntity(body = res))
    }

    @WSMapping(value = "dispatch")
    fun dispatch(req: WsRequestBody, ctx: ChannelHandlerContext, future: CompletableFuture<ResponseEntity?>) {
        log.info("dispatch")
        val from: String? = channelManager.getChannelIdAttributeKey(channel = ctx.channel())
        if (from == null) {
            future.completeExceptionally(RegisterProcessingException(message = "from user not found."))
            return
        }
        req.from = from
        val to: Channel? = channelManager.get(id = req.to)
        if (to == null) {
            future.completeExceptionally(RegisterProcessingException(message = "to user not found."))
            return
        }
        when (req.channelType) {
            WsRequestBody.ChannelType.GROUP -> {
                try {
                    val group: ChannelGroup? = channelGroupManager.getOrCreate(name = req.to)
                    group?.writeAndFlush(TextWebSocketFrame(objectMapper.writeValueAsString(req)))
                } catch (e: JsonProcessingException) {
                    log.error("[WebSocketDispatcher:group] error: ${e.message}")
                }
            }
            WsRequestBody.ChannelType.SINGLE -> {
                try {
                    val channel: Channel? = channelManager.get(id = req.to)
                    channel?.writeAndFlush(TextWebSocketFrame(objectMapper.writeValueAsString(req)))
                } catch (e: JsonProcessingException) {
                    log.error("[WebSocketDispatcher:single] error: ${e.message}")
                }
            }
        }
    }

    @WSMapping(value = "join")
    fun join(req: Group.Request, ctx: ChannelHandlerContext, future: CompletableFuture<ResponseEntity>) {
        log.info("join")
        val joinSuccessful: Boolean = channelGroupManager.getOrCreate(name = req.groupName)
            ?.add(element = ctx.channel()) ?: false
        if (!joinSuccessful) {
            future.completeExceptionally(GroupProcessingException(message = "join to group failure."))
            return
        }
        val res = Group.Response(message = "join")
        future.complete(ResponseEntity(identifier = req.groupName, message = "Done", body = res))
    }

    @WSMapping(value = "leave")
    fun leave(req: Group.Request, ctx: ChannelHandlerContext, future: CompletableFuture<ResponseEntity>) {
        log.info("leave")
        val leaveSuccessful = channelGroupManager.removeChannelInGroup(name = req.groupName, channel = ctx.channel())
        if (!leaveSuccessful) {
            future.completeExceptionally(GroupProcessingException(message = "leave group failure."))
            return
        }
        val res = Group.Response(message = "leave")
        future.complete(ResponseEntity(identifier = req.groupName, message = "Done", body = res))
    }

    @WSMapping(value = "screenshot")
    fun screenshot(req: Screenshot.Request, ctx: ChannelHandlerContext, future: CompletableFuture<ResponseEntity?>) {
        val screenshot: String
        val options = ChromeOptions()
            .also {
                it.addArguments("--no-sandbox")
                //it.addArguments("--headless")
                it.addArguments("--disable-gpu")
            }
        val driver: WebDriver = ChromeDriver(options)
        try {
            driver.get(req.url)
            try {
                (driver as JavascriptExecutor).executeScript("window.scrollBy(0,document.body.scrollHeight)")
            } catch (e: Exception) {
                log.warn("Error during scrolling: ${e.message}")
            }
            driver.findElement(By.tagName("body")).sendKeys(org.openqa.selenium.Keys.ESCAPE)
            screenshot = (driver as TakesScreenshot).getScreenshotAs(OutputType.BASE64)
        } catch (e: Exception) {
            future.completeExceptionally(SeleniumException(message = "${e.message}"))
            return
        } finally {
            driver.quit()
        }
        future.complete(ResponseEntity(body = screenshot))
    }

    private fun allChannelManagers(): List<String> = channelManager.toList()

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
