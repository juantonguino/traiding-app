package com.example.tradingbot.application.service

import com.example.tradingbot.application.port.output.NotificationMessage
import com.example.tradingbot.application.service.strategy.NotificationMessageService
import com.example.tradingbot.domain.exception.EmergencyActiveException
import com.example.tradingbot.domain.exception.OpenTradeExistsException
import com.example.tradingbot.domain.exception.SignalsDisabledException
import com.example.tradingbot.domain.model.SignalStatus
import com.example.tradingbot.domain.service.TradeResultCalculator
import com.example.tradingbot.support.DirectTransactionPort
import com.example.tradingbot.support.FixedClockPort
import com.example.tradingbot.support.InMemoryPaperTradeRepository
import com.example.tradingbot.support.InMemorySignalRepository
import com.example.tradingbot.support.InMemoryTradingState
import com.example.tradingbot.support.NoOpObservabilityPort
import com.example.tradingbot.support.RecordingNotificationPort
import com.example.tradingbot.support.EPOCH
import com.example.tradingbot.support.buySignal
import com.example.tradingbot.support.openTrade
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OpenPaperTradeServiceTest {

    private val signals = InMemorySignalRepository()
    private val trades = InMemoryPaperTradeRepository()
    private val state = InMemoryTradingState()
    private val notifications = RecordingNotificationPort()

    private lateinit var service: OpenPaperTradeService

    @BeforeEach
    fun setUp() {
        val calculator = TradeResultCalculator(BigDecimal("0.001"), BigDecimal("0.001"))
        service = OpenPaperTradeService(
            transactionPort = DirectTransactionPort(),
            tradingStatePort = state,
            paperTradeRepository = trades,
            signalRepository = signals,
            calculator = calculator,
            clockPort = FixedClockPort(EPOCH),
            config = TradingConfig(
                maxOpenTrades = 1,
                entryNotionalUsdt = BigDecimal("100"),
                feePercent = BigDecimal("0.001"),
                slippagePercent = BigDecimal("0.001"),
                stopLossPercent = null,
                takeProfitPercent = null,
                tradeExpirationSeconds = null,
            ),
            notifications = NotificationMessageService(notifications),
            observability = NoOpObservabilityPort(),
        )
    }

    @Test
    fun `opens a paper trade and marks signal used to open`() {
        val signal = buySignal()
        signals.save(signal)

        val trade = service.open(signal)

        assertThat(trade.symbol.value).isEqualTo("BTCUSDT")
        assertThat(trade.quantity.value).isPositive()
        assertThat(state.read().hasOpenTrade()).isTrue()
        assertThat(signals.findById(signal.signalId)!!.status).isEqualTo(SignalStatus.USED_TO_OPEN)
        assertThat(notifications.sent).hasSize(1)
        assertThat(notifications.sent[0]).isInstanceOf(NotificationMessage.TradeOpened::class.java)
    }

    @Test
    fun `rejects a second trade while one is open`() {
        signals.save(buySignal(signalId = "sig-1"))
        val first = service.open(buySignal(signalId = "sig-1"))

        signals.save(buySignal(signalId = "sig-2"))
        assertThatThrownBy { service.open(buySignal(signalId = "sig-2")) }
            .isInstanceOf(OpenTradeExistsException::class.java)
        assertThat(trades.countOpen()).isEqualTo(1)
    }

    @Test
    fun `rejects opening during emergency`() {
        state.setEmergencyActive()
        signals.save(buySignal())

        assertThatThrownBy { service.open(buySignal()) }
            .isInstanceOf(EmergencyActiveException::class.java)
    }

    @Test
    fun `rejects opening when signals disabled`() {
        state.setSignalsEnabled(false, "tester")
        signals.save(buySignal())

        assertThatThrownBy { service.open(buySignal()) }
            .isInstanceOf(SignalsDisabledException::class.java)
    }

    @Test
    fun `applies stop loss and take profit from signal`() {
        signals.save(buySignal())
        val trade = service.open(buySignal())

        assertThat(trade.stopLoss).isNotNull()
        assertThat(trade.takeProfit).isNotNull()
    }

    @Test
    fun `saves trade and returns a closed-able trade object`() {
        signals.save(buySignal())
        val trade = service.open(buySignal())

        assertThat(trades.findByCorrelationId(trade.tradeId)).isNotNull()
        assertThat(trade.dbId).isNotNull()
        assertThat(openTrade().status.name).isEqualTo("OPEN")
    }
}
