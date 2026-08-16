package com.example.tradingbot.adapter.input.binance

import com.example.tradingbot.application.port.output.ClockPort
import com.example.tradingbot.application.port.output.MarketDataPort
import com.example.tradingbot.application.port.output.NotificationMessage
import com.example.tradingbot.application.service.ClosePaperTradeService
import com.example.tradingbot.application.service.GenerateSignalService
import com.example.tradingbot.application.service.OpenPaperTradeService
import com.example.tradingbot.application.service.ProcessClosedCandleService
import com.example.tradingbot.application.service.TradingConfig
import com.example.tradingbot.application.service.strategy.NotificationMessageService
import com.example.tradingbot.application.service.strategy.SignalDecision
import com.example.tradingbot.application.service.strategy.TradingStrategy
import com.example.tradingbot.configuration.TradingProperties
import com.example.tradingbot.domain.model.SignalSide
import com.example.tradingbot.domain.service.SignalEvaluator
import com.example.tradingbot.domain.service.TradeResultCalculator
import com.example.tradingbot.domain.valueobject.Price
import com.example.tradingbot.domain.valueobject.Symbol
import com.example.tradingbot.support.DirectTransactionPort
import com.example.tradingbot.support.EPOCH
import com.example.tradingbot.support.FakeBinanceWsServer
import com.example.tradingbot.support.FixedClockPort
import com.example.tradingbot.support.InMemoryPaperTradeRepository
import com.example.tradingbot.support.InMemorySignalRepository
import com.example.tradingbot.support.InMemoryTradingState
import com.example.tradingbot.support.NoOpObservabilityPort
import com.example.tradingbot.support.RecordingNotificationPort
import com.example.tradingbot.support.price
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal

class KlineListenerIntegrationTest {

    private val signals = InMemorySignalRepository()
    private val trades = InMemoryPaperTradeRepository()
    private val state = InMemoryTradingState()
    private val notifications = RecordingNotificationPort()

    private lateinit var server: FakeBinanceWsServer
    private lateinit var listener: KlineWebSocketListener

    private val closedCandleJson = klineJson(
        openTimeMillis = 1767225600000L,
        isClosed = true,
    )
    private val inProgressCandleJson = klineJson(
        openTimeMillis = 1767226500000L,
        isClosed = false,
    )
    private val duplicateClosedCandleJson = klineJson(
        openTimeMillis = 1767225600000L,
        isClosed = true,
    )

    @BeforeEach
    fun setUp() {
        server = FakeBinanceWsServer()
        server.start()
        listener = listener(server)
        listener.start()
        server.awaitConnection()
    }

    @AfterEach
    fun tearDown() {
        listener.stop()
        server.close()
    }

    @Test
    fun `closed candle is processed into a signal and an open trade`() {
        server.send(closedCandleJson)

        awaitTrue { signals.signals.size == 1 }
        assertThat(trades.findOpenTrade()).isNotNull()
        assertThat(notifications.sent.map { it::class }).contains(
            NotificationMessage.BuySignal::class,
            NotificationMessage.TradeOpened::class,
        )
    }

    @Test
    fun `in-progress candle is ignored`() {
        server.send(closedCandleJson)
        awaitTrue { signals.signals.size == 1 }

        server.send(inProgressCandleJson)

        awaitTrue { signals.signals.size == 1 }
        assertThat(trades.trades).hasSize(1)
        assertThat(signals.signals.single().candleOpenTime).isEqualTo(EPOCH)
    }

    @Test
    fun `duplicate closed candle is not reprocessed`() {
        server.send(closedCandleJson)
        awaitTrue { signals.signals.size == 1 }
        val notificationsBefore = notifications.sent.size

        server.send(duplicateClosedCandleJson)

        Thread.sleep(1000)
        assertThat(signals.signals).hasSize(1)
        assertThat(trades.trades).hasSize(1)
        assertThat(notifications.sent.size).isGreaterThanOrEqualTo(notificationsBefore)
    }

    private fun listener(server: FakeBinanceWsServer): KlineWebSocketListener {
        val properties = TradingProperties().apply {
            binance.wsBaseUrl = "ws://127.0.0.1:${server.port}"
            binance.streamEnabled = true
            symbols = listOf("BTCUSDT")
            timeframe = "15m"
        }
        val calculator = TradeResultCalculator(BigDecimal("0.001"), BigDecimal("0.001"))
        val notificationsService = NotificationMessageService(notifications)
        val generateSignal = GenerateSignalService(
            strategy = strategyReturning(SignalSide.BUY),
            candleHistory = mockk(relaxed = true),
            signalRepository = signals,
            tradingStatePort = state,
            notifications = notificationsService,
            observability = NoOpObservabilityPort(),
        )
        val openTrade = OpenPaperTradeService(
            transactionPort = DirectTransactionPort(),
            tradingStatePort = state,
            paperTradeRepository = trades,
            signalRepository = signals,
            calculator = calculator,
            clockPort = FixedClockPort(EPOCH),
            config = TradingConfig(
                maxOpenTrades = 1,
                entryNotionalUsdt = BigDecimal("100.00"),
                feePercent = BigDecimal("0.001"),
                slippagePercent = BigDecimal("0.001"),
                stopLossPercent = null,
                takeProfitPercent = null,
                tradeExpirationSeconds = null,
            ),
            notifications = notificationsService,
            observability = NoOpObservabilityPort(),
        )
        val closeTrade = ClosePaperTradeService(
            transactionPort = DirectTransactionPort(),
            tradingStatePort = state,
            paperTradeRepository = trades,
            signalRepository = signals,
            marketDataPort = object : MarketDataPort {
                override fun currentPrice(symbol: Symbol): Price = price("63000.0")
            },
            calculator = calculator,
            clockPort = FixedClockPort(EPOCH.plusSeconds(3600)),
            notifications = notificationsService,
            observability = NoOpObservabilityPort(),
        )
        val processClosedCandle = ProcessClosedCandleService(
            generateSignal = generateSignal,
            openPaperTrade = openTrade,
            closePaperTrade = closeTrade,
            signalRepository = signals,
            tradingStatePort = state,
            paperTradeRepository = trades,
            notifications = notificationsService,
            evaluator = SignalEvaluator(),
            observability = NoOpObservabilityPort(),
        )
        return KlineWebSocketListener(
            properties = properties,
            objectMapper = ObjectMapper(),
            binanceKlineMapper = BinanceKlineMapper(),
            processClosedCandle = processClosedCandle,
            observability = NoOpObservabilityPort(),
        )
    }

    private fun strategyReturning(side: SignalSide): TradingStrategy = mockk<TradingStrategy> {
        every { id } returns "sma-rsi"
        every { evaluate(any(), any()) } returns SignalDecision(
            side = side,
            price = price("60500.0"),
            confidence = BigDecimal("85"),
            reason = "test",
            stopLoss = price("59000.0"),
            takeProfit = price("63000.0"),
        )
    }

    private fun klineJson(openTimeMillis: Long, isClosed: Boolean): String =
        """
        {
          "e": "kline",
          "s": "BTCUSDT",
          "k": {
            "t": $openTimeMillis,
            "T": ${openTimeMillis + 900_000},
            "s": "BTCUSDT",
            "i": "15m",
            "o": "60000.0",
            "c": "60500.0",
            "h": "61000.0",
            "l": "59500.0",
            "v": "1.5",
            "x": $isClosed
          }
        }
        """.trimIndent()

    private fun awaitTrue(timeoutSeconds: Long = 10, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        throw AssertionError("Condition not met within ${timeoutSeconds}s")
    }
}
