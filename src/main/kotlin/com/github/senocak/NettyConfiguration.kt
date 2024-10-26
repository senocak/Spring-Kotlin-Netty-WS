package com.github.senocak

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "websocket.netty")
class NettyProperties {
    var socketPath: String = ""
    var maxContentLength: Int = 65536
    var port: Int = 8090
    var bossThread: Int = 1
    var workerThread: Int = 2
}
