package com.example.tradingbot.adapter.input.binance

import com.example.tradingbot.adapter.input.binance.dto.BinanceKlineEvent
import com.example.tradingbot.application.port.input.ProcessClosedCandleUseCase
import com.example.tradingbot.application.port.output.MarketDataConnectionPort
import com.example.tradingbot.application.port.output.ObservabilityPort
import com.example.tradingbot.configuration.TradingProperties
import tools.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.random.Random

@Component
class KlineWebSocketListener(
    private val properties: TradingProperties,
    private val objectMapper: ObjectMapper,
    private val binanceKlineMapper: BinanceKlineMapper,
    private val processClosedCandle: ProcessClosedCandleUseCase,
    private val observability: ObservabilityPort,
) : MarketDataConnectionPort {

    private val logger = LoggerFactory.getLogger(KlineWebSocketListener::class.java)
    private val running = AtomicBoolean(true)
    private val webSocketClient = StandardWebSocketClient()

    @Volatile
    private var lastMessageAtMillis: Long = 0L

    @Volatile
    private var activeSession: WebSocketSession? = null

    fun lastMessageAt(): Long = lastMessageAtMillis

    fun stop() {
        running.set(false)
        activeSession?.close(CloseStatus.GOING_AWAY)
    }

    override fun isConnected(): Boolean {
        val now = System.currentTimeMillis()
        return lastMessageAtMillis > 0 && (now - lastMessageAtMillis) < properties.binance.dataAbsenceWindow.toMillis()
    }

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        if (!properties.binance.streamEnabled) {
            logger.info("Binance WebSocket stream disabled by configuration")
            return
        }
        properties.symbols.forEach { symbol ->
            Thread({ connectLoop(symbol) }, "binance-ws-$symbol").start()
        }
    }

    private fun connectLoop(symbol: String) {
        val timeframe = properties.timeframe
        val url = "${properties.binance.wsBaseUrl}/ws/${symbol.lowercase()}@kline_$timeframe"
        val backoffMillis = AtomicLong(1000L)
        while (running.get()) {
            try {
                logger.info("Connecting to Binance stream: {}", url)
                val handler = KlineHandler(symbol, url)
                val session = webSocketClient.execute(handler, url).get()
                activeSession = session
                handler.awaitClosed()
                activeSession = null
                logger.warn("Binance stream disconnected: {}", url)
            } catch (e: Exception) {
                observability.adapterError("binance", "websocket_connect")
                logger.error("Binance stream error for {}: {}", url, e.message)
            }
            sleepWithBackoff(backoffMillis)
        }
    }

    private fun sleepWithBackoff(backoffMillis: AtomicLong) {
        val base = backoffMillis.get()
        val max = properties.binance.reconnectMaxBackoff.toMillis()
        val jitter = Random.nextLong(0, max(1, base / 4))
        try {
            Thread.sleep(base + jitter)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return
        }
        val next = (base * 2).coerceAtMost(max)
        backoffMillis.set(next)
    }

    private inner class KlineHandler(
        private val symbol: String,
        private val streamUrl: String,
    ) : TextWebSocketHandler() {

        private val closedLatch = CountDownLatch(1)

        fun awaitClosed() {
            closedLatch.await()
        }

        override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
            closedLatch.countDown()
        }

        override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
            lastMessageAtMillis = System.currentTimeMillis()
            try {
                val event = objectMapper.readValue(message.payload, BinanceKlineEvent::class.java)
                if (event.k?.isClosed != true) {
                    return
                }
                val candle = binanceKlineMapper.toMarketCandle(event)
                if (candle.symbol.value != symbol.uppercase()) {
                    logger.warn("Stream {} delivered candle for {}; ignoring", streamUrl, candle.symbol.value)
                    return
                }
                MDC.put("correlationId", "candle-${candle.symbol.value}-${candle.openTime}")
                processClosedCandle.process(candle)
            } catch (e: Exception) {
                observability.adapterError("binance", "kline_parse")
                logger.error("Failed to process Binance kline message from {}: {}", streamUrl, e.message)
            } finally {
                MDC.remove("correlationId")
            }
        }
    }
}
