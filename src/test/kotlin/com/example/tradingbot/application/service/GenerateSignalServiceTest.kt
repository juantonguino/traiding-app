package com.example.tradingbot.application.service

import com.example.tradingbot.application.port.output.NotificationMessage
import com.example.tradingbot.application.service.strategy.NotificationMessageService
import com.example.tradingbot.application.service.strategy.SignalDecision
import com.example.tradingbot.application.service.strategy.TradingStrategy
import com.example.tradingbot.domain.model.SignalSide
import com.example.tradingbot.domain.model.SignalStatus
import com.example.tradingbot.domain.valueobject.SignalId
import com.example.tradingbot.support.InMemorySignalRepository
import com.example.tradingbot.support.InMemoryTradingState
import com.example.tradingbot.support.NoOpObservabilityPort
import com.example.tradingbot.support.RecordingNotificationPort
import com.example.tradingbot.support.candle
import com.example.tradingbot.support.price
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class GenerateSignalServiceTest {

    private val signals = InMemorySignalRepository()
    private val state = InMemoryTradingState()
    private val notifications = RecordingNotificationPort()

    private fun service(strategy: TradingStrategy): GenerateSignalService = GenerateSignalService(
        strategy = strategy,
        candleHistory = mockk(relaxed = true),
        signalRepository = signals,
        tradingStatePort = state,
        notifications = NotificationMessageService(notifications),
        observability = NoOpObservabilityPort(),
    )

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

    @Test
    fun `generates buy signal when strategy says buy`() {
        val generated = service(strategyReturning(SignalSide.BUY)).generate(candle())

        assertThat(generated).isNotNull()
        assertThat(generated!!.side).isEqualTo(SignalSide.BUY)
        assertThat(generated.status).isEqualTo(SignalStatus.GENERATED)
        assertThat(signals.signals).hasSize(1)
        assertThat(notifications.sent).hasSize(1)
        assertThat(notifications.sent[0]).isInstanceOf(NotificationMessage.BuySignal::class.java)
    }

    @Test
    fun `returns null when signals disabled`() {
        state.setSignalsEnabled(false, "tester")
        val generated = service(strategyReturning(SignalSide.BUY)).generate(candle())

        assertThat(generated).isNull()
        assertThat(signals.signals).isEmpty()
    }

    @Test
    fun `returns null during emergency`() {
        state.setEmergencyActive()
        val generated = service(strategyReturning(SignalSide.BUY)).generate(candle())

        assertThat(generated).isNull()
    }

    @Test
    fun `returns existing signal for duplicate candle`() {
        val service = service(strategyReturning(SignalSide.BUY))
        val first = service.generate(candle())!!
        signals.update(first.asAccepted())

        val second = service.generate(candle())

        assertThat(second!!.signalId).isEqualTo(first.signalId)
        assertThat(signals.signals).hasSize(1)
    }

    @Test
    fun `persists generated signal with ignoreReason null`() {
        val generated = service(strategyReturning(SignalSide.BUY)).generate(candle())!!

        val persisted = signals.findById(SignalId(generated.signalId.value))!!
        assertThat(persisted.ignoreReason).isNull()
        assertThat(persisted.stopLoss).isNotNull()
        assertThat(persisted.takeProfit).isNotNull()
    }
}
