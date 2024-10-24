package com.github.senocak
/*
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.bonigarcia.wdm.WebDriverManager
import jakarta.annotation.PostConstruct
import org.openqa.selenium.By
import org.openqa.selenium.Dimension
import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.util.StringUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.PingMessage
import org.springframework.web.socket.PongMessage
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.handler.AbstractWebSocketHandler
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@SpringBootApplication
@RestController
@RequestMapping("/api")
class SpringKotlinWsScreenshotApplication {
    private lateinit var driver: WebDriver

    @GetMapping
    @PostConstruct
    fun create() {
        WebDriverManager.chromedriver().setup()
        screenshotForUrlTab(
            url = "https://github.com/elestio/ws-screenshot/blob/master/API/shared.js",
            isFullPage = true
        )
    }

    fun screenshotForUrlTab(
        url: String,
        isFullPage: Boolean,
        resX: Int? = null,
        resY: Int? = null,
        outFormat: String = "png",
        orientation: String = "landscape",
        waitTime: Long = 100L,
        proxyServer: String? = null,
        dismissModals: Boolean = false
    ): Map<String, Any?> {
        val response = mutableMapOf<String, Any?>()
        try {
            val options = ChromeOptions()
                .also {
                    //it.addArguments("--no-sandbox", "--headless", "--disable-gpu")
                    if (proxyServer != null)
                        it.addArguments("--proxy-server=$proxyServer")
                }
            driver = ChromeDriver(options)
            //driver.manage().timeouts().pageLoadTimeout(Duration.ofMillis(TimeUnit.MILLISECONDS.toMillis(waitTime)))
            //driver.manage().timeouts().pageLoadTimeout(waitTimeMutable, TimeUnit.MILLISECONDS)
            driver.get(url)

            if (resX != null && resX > 0 && resY != null && resY > 0)
                driver.manage().window().size = Dimension(resX, resY)

            if (isFullPage)
                autoScroll()

            // Optionally dismiss modals
            if (dismissModals)
                driver.findElement(By.tagName("body")).sendKeys(org.openqa.selenium.Keys.ESCAPE)

            val ts = driver as TakesScreenshot
            var screenshot = ""
            when (outFormat) {
                "jpg", "jpeg" -> screenshot = ts.getScreenshotAs(OutputType.BASE64)
                "png" -> screenshot = ts.getScreenshotAs(OutputType.BASE64)
                "pdf" -> throw RuntimeException("not supported")
            }
            response["mimeType"] = "image/$outFormat"
            response["data"] = screenshot
        } catch (e: Exception) {
            response["status"] = "error"
            response["details"] = e.message
        } finally {
            driver.quit()
            //driver = null
        }
        return response
    }

    private fun autoScroll() {
        try {
            (driver as? org.openqa.selenium.JavascriptExecutor)
            ?.executeScript("window.scrollBy(0,document.body.scrollHeight)")
        } catch (e: Exception) {
            println("Error during scrolling: ${e.message}")
        }
    }

}

fun main(args: Array<String>) {
    runApplication<SpringKotlinWsScreenshotApplication>(*args)
}
fun <R : Any> R.logger(): Lazy<Logger> = lazy {
    LoggerFactory.getLogger((if (javaClass.kotlin.isCompanion) javaClass.enclosingClass else javaClass).name)
}

@Configuration
@EnableWebSocket
class WebsocketConfig(
    private val objectMapper: ObjectMapper
): WebSocketConfigurer, AbstractWebSocketHandler() {
    /**
     * Register websocket handlers.
     * @param registry WebSocketHandlerRegistry
     */
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(this, "/ws").setAllowedOrigins("*")
    }

    private val log: Logger by logger()
    private val lock = ReentrantLock(true)
    private val userSessionCache: MutableMap<String, WebsocketIdentifier> = ConcurrentHashMap<String, WebsocketIdentifier>()

    /**
     * A method that is called when a new WebSocket session is created.
     * @param session The new WebSocket session.
     */
    override fun afterConnectionEstablished(session: WebSocketSession): Unit =
        lock.withLock {
            runCatching {
                log.info("Websocket connection established. Path: ${session.uri!!.path}")
                if (session.uri == null)
                    log.error("Unable to retrieve the websocket session; serious error!").also { return }
                val queryParams: Map<String, String> = session.uri!!.query.getQueryParams() ?: throw Exception("QueryParams can not be empty")
                val email = queryParams["email"]
                WebsocketIdentifier(user = email!!, session = session)
                    .also { log.info("Websocket session established: $it") }
                    .run { put(data = this) }
            }.onFailure {
                log.error("A serious error has occurred with websocket post-connection handling. Ex: ${it.message}")
            }
        }

    /**
     * A method that is called when a WebSocket session is closed.
     * @param session The WebSocket session that is closed.
     * @param status The status of the close.
     */
    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus): Unit =
        lock.withLock {
            log.info("Websocket connection closed. Path: ${session.uri!!.path}")
            runCatching {
                if (session.uri == null)
                    log.error("Unable to retrieve the websocket session; serious error!").also { return }
                val queryParams: Map<String, String> = session.uri!!.query.getQueryParams() ?: throw Exception("QueryParams can not be empty")
                val email = queryParams["email"]
                deleteSession(key = email!!)
                    .also { log.info("Websocket for $email has been closed") }
            }.onFailure {
                log.error("Error occurred while closing websocket channel:${it.message}")
            }
        }

    override fun handleMessage(session: WebSocketSession, message: WebSocketMessage<*>) {
        when (message) {
            is PingMessage -> log.info("PingMessage: $message")
            is PongMessage -> log.info("PongMessage: $message")
            is BinaryMessage -> log.info("BinaryMessage: $message")
            is TextMessage -> {
                val body: WsRequestBody = objectMapper.readValue(message.payload, WsRequestBody::class.java)
                log.info("TextMessage: $body")
                try {
                    val requestBody: WsRequestBody = objectMapper.readValue(message.payload, WsRequestBody::class.java)
                    val queryParams: Map<String, String> = session.uri!!.query.getQueryParams() ?: throw Exception("QueryParams can not be empty")
                    requestBody.from = queryParams["email"]
                    sendPrivateMessage(requestBody = requestBody)
                    log.info("Websocket message sent: ${message.payload}")
                } catch (ex: Exception) {
                    log.error("Unable to parse request body; Exception: ${ex.message}")
                }
            }
            else -> session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Not supported"))
                .also { log.error("Not supported. ${message.javaClass}") }
        }
    }

    /**
     * Get all websocket session cache.
     * @return map of websocket session cache.
     */
    val allWebSocketSession: Map<String, WebsocketIdentifier> get() = userSessionCache

    /**
     * Add websocket session cache.
     * @param data websocket session cache.
     */
    fun put(data: WebsocketIdentifier) {
        userSessionCache[data.user] = data
        broadCastMessage(message = data.user, type = "login")
        broadCastAllUserList(to = data.user)
    }

    /**
     * Get or default websocket session cache.
     * @param key key of websocket session cache.
     * @return websocket session cache.
     */
    fun getOrDefault(key: String): WebsocketIdentifier? =
        userSessionCache.getOrDefault(key = key, defaultValue = null)

    /**
     * Remove websocket session cache.
     * @param key key of websocket session cache.
     */
    fun deleteSession(key: String) {
        val websocketIdentifier: WebsocketIdentifier? = getOrDefault(key = key)
        if (websocketIdentifier?.session == null) {
            log.error("Unable to remove the websocket session; serious error!")
            return
        }
        userSessionCache.remove(key = key)
        broadCastAllUserList(to = websocketIdentifier.user)
        broadCastMessage(message = websocketIdentifier.user, type = "logout")
    }

    /**
     * Broadcast message to all websocket session cache.
     * @param message message to broadcast.
     */
    private fun broadCastMessage(message: String, type: String) {
        val wsRequestBody = WsRequestBody()
            .also {
                it.content = message
                it.date = Instant.now().toEpochMilli()
                it.type = type
            }
        allWebSocketSession.forEach { entry ->
            try {
                entry.value.session!!.sendMessage(TextMessage(objectMapper.writeValueAsString(wsRequestBody)))
            } catch (e: Exception) {
                log.error("Exception while broadcasting: ${e.message}")
            }
        }
    }

    /**
     * Broadcast message to specific websocket session cache.
     * @param requestBody message to send.
     */
    fun sendPrivateMessage(requestBody: WsRequestBody) {
        val userTo: WebsocketIdentifier? = getOrDefault(key = requestBody.to!!)
        if (userTo?.session == null) {
            log.error("User or Session not found in cache for user: ${requestBody.to}, returning...")
            return
        }
        requestBody.type = "private"
        requestBody.date = Instant.now().toEpochMilli()
        try {
            userTo.session!!.sendMessage(TextMessage(objectMapper.writeValueAsString(requestBody)))
        } catch (e: IOException) {
            log.error("Exception while sending message: ${e.message}")
        }
    }

    /**
     * Broadcast message to specific websocket session cache.
     * @param from from user.
     * @param payload message to send.
     */
    fun sendMessage(from: String?, to: String, type: String?, payload: String?) {
        val userTo: WebsocketIdentifier? = getOrDefault(key = to)
        if (userTo?.session == null) {
            log.error("User or Session not found in cache for user: $to, returning...")
            return
        }
        val requestBody: WsRequestBody = WsRequestBody()
            .also {
                it.from = from
                it.to = to
                it.date = Instant.now().toEpochMilli()
                it.content = payload
                it.type = type
            }
        try {
            userTo.session!!.sendMessage(TextMessage(objectMapper.writeValueAsString(requestBody)))
        } catch (e: IOException) {
            log.error("Exception while sending message: ${e.message}")
        }
    }

    /**
     * Broadcast message to all websocket session cache.
     * @param to user to broadcast.
     */
    private fun broadCastAllUserList(to: String): Unit =
        sendMessage(from = null, to = to, type = "online", payload = userSessionCache.keys.joinToString(","))

    /**
     * this is scheduled to run every in 10_000 milliseconds period // every 10 seconds
     */
    @Async
    @Scheduled(fixedRate = 10_000)
    fun pingWs() {
        MDC.put("userId", "scheduler")
        allWebSocketSession.forEach { it: Map.Entry<String, WebsocketIdentifier> ->
            try {
                it.value.session!!.sendMessage(PingMessage())
                log.info("Pinged user with key: ${it.key}, and session: ${it.value}")
            } catch (e: Exception) {
                log.error("Exception occurred for sending ping message: ${e.message}")
                deleteSession(key = it.key)
            }
        }
        MDC.remove("userId")
    }

    data class WebsocketIdentifier(
        var user: String,
        var session: WebSocketSession? = null
    )
    data class WsRequestBody(
        var from: String? = null,
        var to: String? = null,
        var content: String? = null,
        var type: String? = null,
        var date: Long? = null
    )
    fun String.split(delimiter: String): Array<String>? = StringUtils.split(this, delimiter)
    fun String.getQueryParams(): Map<String, String>? {
        val queryParams: MutableMap<String, String> = LinkedHashMap()
        return when {
            this.isEmpty() -> null
            else -> {
                val split: Array<String>? = this.split(delimiter = "&")
                if (!split.isNullOrEmpty()) {
                    for (param: String in split) {
                        val paramArray: Array<String>? = param.split(delimiter = "=")
                        queryParams[paramArray!![0]] = paramArray[1]
                    }
                } else {
                    val paramArray: Array<String>? = this.split(delimiter = "=")
                    queryParams[paramArray!![0]] = paramArray[1]
                }
                queryParams
            }
        }
    }
}
*/