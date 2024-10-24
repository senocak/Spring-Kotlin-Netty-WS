package com.github.senocak

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
    fromApplication<SpringKotlinWsScreenshotApplication>().with(TestcontainersConfiguration::class).run(*args)
}
