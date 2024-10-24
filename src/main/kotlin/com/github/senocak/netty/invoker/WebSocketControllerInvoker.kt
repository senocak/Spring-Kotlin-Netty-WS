package com.github.senocak.netty.invoker

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.senocak.netty.WSController
import com.github.senocak.netty.WSMapping
import com.github.senocak.netty.RequestEntity
import com.github.senocak.netty.ResponseEntity
import com.github.senocak.netty.WebSocketProcessingException
import io.netty.channel.ChannelHandlerContext
import org.apache.commons.lang3.StringUtils
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.lang.reflect.Method
import java.util.Arrays
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

@Component
class WebSocketControllerInvoker(
    context: ApplicationContext,
    val objectMapper: ObjectMapper
) {
    private val controllerBeans: List<Any> =
        CopyOnWriteArrayList(context.getBeansWithAnnotation(WSController::class.java).values)

    fun invoke(req: RequestEntity, ctx: ChannelHandlerContext?,
               future: CompletableFuture<ResponseEntity?>): CompletableFuture<ResponseEntity?> {
        var controller: Method? = null
        for (bean in controllerBeans) {
            controller = getController(bean = bean, name = req.mapper)
            if (controller != null) {
                try {
                    val parameters = controller.parameters
                    if (parameters != null) {
                        val parameter = parameters[0]
                        if (!StringUtils.contains(parameter.type.name, "ChannelHandlerContext")) {
                            val arg: Any = objectMapper.convertValue(req.body,
                                objectMapper.constructType(parameter.type))
                                ?: throw NullPointerException("parameter can not be a null.")
                            controller.invoke(bean, arg, ctx, future)
                        }
                    } else {
                        controller.invoke(bean, ctx, future)
                    }
                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
                break
            }
        }
        if (controller == null)
            future.completeExceptionally(WebSocketProcessingException("websocket controller not found."))
        return future
    }

    private fun getController(bean: Any, name: String): Method? {
        return Arrays.stream(bean.javaClass.declaredMethods)
            .filter { StringUtils.equalsIgnoreCase(name, it.getAnnotation(WSMapping::class.java).value) }
            .findFirst().orElse(null)
    }
}