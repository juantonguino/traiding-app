package com.example.tradingbot.application.service

import com.example.tradingbot.application.port.output.ClockPort
import com.example.tradingbot.application.port.output.MarketDataPort
import com.example.tradingbot.application.service.strategy.NotificationMessageService
import com.example.tradingbot.domain.model.CloseReason
import com.example.tradingbot.domain.model.SignalStatus
import com.example.tradingbot.domain.service.TradeResultCalculator
import com.example.tradingbot.domain.valueobject.Price
import com.example.tradingbot.domain.valueobject.Symbol
import com.example.tradingbot.support.DirectTransactionPort
import com.example.tradingbot.support.EPOCH
import com.example.tradingbot.support.FixedClockPort
import com.example.tradingbot.support.InMemoryPaperTradeRepository
import com.example.tradingbot.support.InMemorySignalRepository
import com.example.tradingbot.support.InMemoryTradingState
import com.example.tradingbot.support.NoOpObservabilityPort
import com.example.tradingbot.support.RecordingNotificationPort
import com.example.tradingbot.support.buySignal
import com.example.tradingbot.support.price
import com.example.tradingbot.support.sellSignal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class HistoryAuditTest {

    private val signals = InMemorySignalRepository()
    private val trades = InMemoryPaperTradeRepository()
    private val state = InMemoryTradingState()
    private val notifications = RecordingNotificationPort()

    private lateinit var openService: OpenPaperTradeService
    private lateinit var closeService: ClosePaperTradeService

    @BeforeEach
    fun setUp() {
        val calculator = TradeResultCalculator(BigDecimal("0.001"), BigDecimal("0.001"))
        openService = OpenPaperTradeService(
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
            notifications = NotificationMessageService(notifications),
            observability = NoOpObservabilityPort(),
        )
        closeService = ClosePaperTradeService(
            transactionPort = DirectTransactionPort(),
            tradingStatePort = state,
            paperTradeRepository = trades,
            signalRepository = signals,
            marketDataPort = object : MarketDataPort {
                override fun currentPrice(symbol: Symbol): Price = price("63000.0")
            },
            calculator = calculator,
            clockPort = FixedClockPort(EPOCH.plusSeconds(3600)),
            notifications = NotificationMessageService(notifications),
            observability = NoOpObservabilityPort(),
        )
    }

    @Test
    fun `open trade preserves the signal reference used to open`() {
        val openSignal = signals.save(buySignal().asAccepted())

        val trade = openService.open(openSignal)

        assertThat(trade.openSignalId).isEqualTo(openSignal.signalId)
        assertThat(signals.findById(openSignal.signalId)!!.status).isEqualTo(SignalStatus.USED_TO_OPEN)
        val persisted = trades.findByCorrelationId(trade.tradeId)!!
        assertThat(persisted.openSignalId).isEqualTo(openSignal.signalId)
    }

    @Test
    fun `closed trade preserves both open and close signal references`() {
        val openSignal = signals.save(buySignal().asAccepted())
        val trade = openService.open(openSignal)
        val closeSignal = signals.save(sellSignal())

        val closed = closeService.closeBySellSignal(closeSignal)!!

        assertThat(closed.openSignalId).isEqualTo(openSignal.signalId)
        assertThat(closed.closeSignalId).isEqualTo(closeSignal.signalId)
        assertThat(signals.findById(openSignal.signalId)!!.status).isEqualTo(SignalStatus.USED_TO_OPEN)
        assertThat(signals.findById(closeSignal.signalId)!!.status).isEqualTo(SignalStatus.USED_TO_CLOSE)
    }

    @Test
    fun `audit trail reconstructs open and close signals from trade references`() {
        val openSignal = signals.save(buySignal().asAccepted())
        val trade = openService.open(openSignal)
        val closeSignal = signals.save(sellSignal())
        val closed = closeService.closeBySellSignal(closeSignal)!!

        val reconstructedOpen = signals.findById(closed.openSignalId)
        val reconstructedClose = closed.closeSignalId?.let(signals::findById)

        assertThat(reconstructedOpen).isNotNull()
        assertThat(reconstructedOpen!!.symbol).isEqualTo(closed.symbol)
        assertThat(reconstructedOpen.status).isEqualTo(SignalStatus.USED_TO_OPEN)
        assertThat(reconstructedClose).isNotNull()
        assertThat(reconstructedClose!!.side.name).isEqualTo("SELL")
        assertThat(reconstructedClose.status).isEqualTo(SignalStatus.USED_TO_CLOSE)
        assertThat(trades.trades).hasSize(1)
        assertThat(trades.trades[0].closeReason).isEqualTo(CloseReason.SELL_SIGNAL)
    }
}
