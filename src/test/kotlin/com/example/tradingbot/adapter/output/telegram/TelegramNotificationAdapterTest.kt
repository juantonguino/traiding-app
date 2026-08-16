package com.example.tradingbot.adapter.output.telegram

import com.example.tradingbot.application.port.output.NotificationMessage
import com.example.tradingbot.configuration.TradingProperties
import com.example.tradingbot.support.NoOpObservabilityPort
import com.example.tradingbot.support.buySignal
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TelegramNotificationAdapterTest {

    private lateinit var server: MockWebServer
    private val successObserved = AtomicBoolean(false)
    private val failureObserved = AtomicBoolean(false)

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter(server: MockWebServer): Pair<TelegramNotificationAdapter, CountDownLatch> {
        val props = TradingProperties().apply {
            telegram.apiBaseUrl = server.url("/").toString().trimEnd('/')
            telegram.token = "SECRET_TOKEN"
            telegram.chatId = "123456789"
            telegram.timeout = Duration.ofSeconds(5)
            telegram.maxRetries = 1
        }
        val latch = CountDownLatch(1)
        val adapter = object : TelegramNotificationAdapter(
            props,
            WebClient.builder().build(),
            NoOpObservabilityPort(),
            Retry.ofDefaults("telegram"),
            CircuitBreaker.ofDefaults("telegram"),
        ) {
            override fun send(message: NotificationMessage) {
                try {
                    super.send(message)
                } finally {
                    latch.countDown()
                }
            }
        }
        return adapter to latch
    }

    @Test
    fun `sends text with parse mode and token in path on success`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true,"result":{"message_id":1}}"""))
        val (adapter, latch) = adapter(server)

        adapter.send(NotificationMessage.BuySignal(buySignal()))

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)
        assertThat(recorded).isNotNull()
        assertThat(recorded!!.path).startsWith("/botSECRET_TOKEN/sendMessage")
        assertThat(recorded.body.readUtf8())
            .contains("\"chat_id\":\"123456789\"")
            .contains("\"parse_mode\":\"HTML\"")
        awaitTrue { adapter.lastSendOk }
    }

    @Test
    fun `marks last send as failed when server errors`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        val (adapter, latch) = adapter(server)

        adapter.send(NotificationMessage.BuySignal(buySignal()))

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
        awaitTrue { !adapter.lastSendOk }
    }

    private fun awaitTrue(timeoutSeconds: Long = 10, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        throw AssertionError("Condition not met within ${timeoutSeconds}s")
    }

    @Test
    fun `does not send when disabled`() {
        val props = TradingProperties().apply {
            telegram.enabled = false
            telegram.token = "SECRET_TOKEN"
            telegram.chatId = "123456789"
        }
        val adapter = TelegramNotificationAdapter(
            props,
            WebClient.builder().build(),
            NoOpObservabilityPort(),
            Retry.ofDefaults("telegram"),
            CircuitBreaker.ofDefaults("telegram"),
        )

        adapter.send(NotificationMessage.BuySignal(buySignal()))

        assertThat(server.requestCount).isZero()
        assertThat(adapter.health().status.code).isEqualTo("UP")
    }
}
