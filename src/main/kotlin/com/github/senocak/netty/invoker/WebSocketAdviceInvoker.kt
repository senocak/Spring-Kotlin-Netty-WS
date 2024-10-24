package com.github.senocak.netty.invoker

import com.github.senocak.netty.WSControllerAdvice
import com.github.senocak.netty.WSExceptionHandler
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArrayList

@Component
class WebSocketAdviceInvoker(context: ApplicationContext) {
    private val adviceBeans: List<Any> =
        CopyOnWriteArrayList(context.getBeansWithAnnotation(WSControllerAdvice::class.java).values)

    @Throws(InvocationTargetException::class, IllegalAccessException::class)
    fun invoke(throwable: Throwable?): Any? {
        return when (throwable) {
            null -> null
            else -> {
                for (bean in adviceBeans) {
                    val advisor = getAdvisor(bean = bean, throwable = throwable)
                    if (advisor != null)
                        return advisor.invoke(bean, throwable)
                }
                null
            }
        }
    }

    private fun getAdvisor(bean: Any, throwable: Throwable?): Method? {
        return when {
            throwable == null -> null
            else -> {
                bean::class.java.declaredMethods.forEach { method ->
                    method.getAnnotation(WSExceptionHandler::class.java).throwables.forEach { cls ->
                        if (throwable::class.java.name == cls.java.name) {
                            return method
                        }
                    }
                }
                null
            }
        }
    }
}
