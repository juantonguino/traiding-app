package com.example.tradingbot.adapter.output.telegram

import com.example.tradingbot.application.port.output.NotificationMessage
import com.example.tradingbot.application.port.output.NotificationPort
import com.example.tradingbot.application.port.output.ObservabilityPort
import com.example.tradingbot.configuration.TradingProperties
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreaker.decorateCheckedSupplier
import io.github.resilience4j.core.functions.CheckedSupplier
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.Retry.decorateCheckedSupplier
import org.slf4j.LoggerFactory
import org.springframework.boot.health.contributor.Health
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class TelegramNotificationAdapter(
    private val properties: TradingProperties,
    private val webClient: WebClient,
    private val observability: ObservabilityPort,
    @Qualifier("telegramRetry") private val retry: Retry,
    @Qualifier("telegramCircuitBreaker") private val circuitBreaker: CircuitBreaker,
) : NotificationPort {

    private val logger = LoggerFactory.getLogger(TelegramNotificationAdapter::class.java)

    @Volatile
    var lastSendOk: Boolean = true

    private val token: String? = properties.telegram.token
    private val chatId: String? = properties.telegram.chatId
    private val baseUri: java.net.URI = java.net.URI(properties.telegram.apiBaseUrl)

    private val enabled: Boolean =
        properties.telegram.enabled && !token.isNullOrBlank() && !chatId.isNullOrBlank()

    init {
        if (!enabled) {
            logger.warn("Telegram notifications disabled: token/chatId missing or enabled=true absent (analysis continues)")
        }
    }

    override fun send(message: NotificationMessage) {
        if (!enabled) {
            logger.debug("Skipping Telegram notification (disabled): type={}", message::class.simpleName)
            return
        }
        Thread.ofVirtual().name("telegram-sender").start { doSend(message) }
    }

    private fun doSend(message: NotificationMessage) {
        try {
            val supplier = CheckedSupplier<String?> {
                webClient.post()
                    .uri { uriBuilder ->
                        uriBuilder.scheme(baseUri.scheme).host(baseUri.host)
                        if (baseUri.port != -1) {
                            uriBuilder.port(baseUri.port)
                        }
                        uriBuilder.path("/bot${token!!}/sendMessage").build()
                    }
                    .bodyValue(mapOf("chat_id" to chatId, "text" to message.text(), "parse_mode" to "HTML"))
                    .retrieve()
                    .bodyToMono(String::class.java)
                    .timeout(properties.telegram.timeout)
                    .block()
            }
            val decorated = decorateCheckedSupplier(circuitBreaker, decorateCheckedSupplier(retry, supplier))
            decorated.get()
            lastSendOk = true
            logger.info("Telegram notification sent: type={} correlationId={}", message::class.simpleName, message.correlationId)
        } catch (e: Exception) {
            lastSendOk = false
            observability.adapterError("telegram", "sendMessage")
            logger.error(
                "Telegram notification FAILED: type={} correlationId={} errorClass={} (details and token never logged)",
                message::class.simpleName, message.correlationId, e::class.java.simpleName,
            )
        }
    }

    fun health(): Health {
        if (!enabled) {
            return Health.up().withDetail("telegram", "disabled").build()
        }
        return if (lastSendOk) {
            Health.up().build()
        } else {
            Health.down().withDetail("reason", "last send failed").build()
        }
    }
}
