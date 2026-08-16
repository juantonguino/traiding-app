package com.example.tradingbot.application.service

import com.example.tradingbot.application.port.input.ClosePaperTradeUseCase
import com.example.tradingbot.application.port.output.MarketDataPort
import com.example.tradingbot.application.port.output.NotificationMessage
import com.example.tradingbot.application.service.strategy.NotificationMessageService
import com.example.tradingbot.domain.exception.NoOpenTradeException
import com.example.tradingbot.domain.model.CloseReason
import com.example.tradingbot.domain.model.PaperTrade
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
import com.example.tradingbot.support.openTrade
import com.example.tradingbot.support.price
import com.example.tradingbot.support.sellSignal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ClosePaperTradeServiceTest {

    private val signals = InMemorySignalRepository()
    private val trades = InMemoryPaperTradeRepository()
    private val state = InMemoryTradingState()
    private val notifications = RecordingNotificationPort()

    private lateinit var service: ClosePaperTradeUseCase

    @BeforeEach
    fun setUp() {
        val calculator = TradeResultCalculator(BigDecimal("0.001"), BigDecimal("0.001"))
        service = ClosePaperTradeService(
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

    private fun withOpenTrade(): PaperTrade {
        val trade = openTrade()
        trades.save(trade)
        state.markOpen(1L, trade.symbol)
        return trade
    }

    @Test
    fun `closes current trade at market price with manual reason`() {
        withOpenTrade()

        val closed = service.closeCurrent(CloseReason.MANUAL_CLOSE, actor = "tester")

        assertThat(closed.status.name).isEqualTo("CLOSED")
        assertThat(closed.exitPrice!!.value).isEqualByComparingTo("63000.0")
        assertThat(closed.closeReason).isEqualTo(CloseReason.MANUAL_CLOSE)
        assertThat(closed.netPnl).isNotNull()
        assertThat(state.read().hasOpenTrade()).isFalse()
        assertThat(notifications.sent).hasSize(2)
        assertThat(notifications.sent[0]).isInstanceOf(NotificationMessage.TradeClosed::class.java)
        assertThat(notifications.sent[1]).isInstanceOf(NotificationMessage.GainOrLoss::class.java)
    }

    @Test
    fun `throws when no open trade`() {
        assertThatThrownBy { service.closeCurrent(CloseReason.MANUAL_CLOSE, actor = "tester") }
            .isInstanceOf(NoOpenTradeException::class.java)
    }

    @Test
    fun `closes by sell signal and marks signal used to close`() {
        withOpenTrade()
        val signal = sellSignal()
        signals.save(signal)

        val closed = service.closeBySellSignal(signal)

        assertThat(closed).isNotNull()
        assertThat(closed!!.closeReason).isEqualTo(CloseReason.SELL_SIGNAL)
        assertThat(closed.closeSignalId).isEqualTo(signal.signalId)
        assertThat(signals.findById(signal.signalId)!!.status).isEqualTo(SignalStatus.USED_TO_CLOSE)
    }

    @Test
    fun `sell signal with no matching open trade returns null`() {
        val signal = sellSignal()
        signals.save(signal)

        val closed = service.closeBySellSignal(signal)

        assertThat(closed).isNull()
    }
}
