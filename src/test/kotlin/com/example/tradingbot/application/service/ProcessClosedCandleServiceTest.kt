package com.example.tradingbot.application.service

import com.example.tradingbot.application.port.input.ClosePaperTradeUseCase
import com.example.tradingbot.application.port.input.GenerateSignalUseCase
import com.example.tradingbot.application.port.input.OpenPaperTradeUseCase
import com.example.tradingbot.application.port.output.NotificationMessage
import com.example.tradingbot.application.service.strategy.NotificationMessageService
import com.example.tradingbot.domain.exception.OpenTradeExistsException
import com.example.tradingbot.domain.model.IgnoreReasons
import com.example.tradingbot.domain.model.SignalSide
import com.example.tradingbot.domain.model.SignalStatus
import com.example.tradingbot.domain.service.SignalEvaluator
import com.example.tradingbot.support.BTC
import com.example.tradingbot.support.InMemoryPaperTradeRepository
import com.example.tradingbot.support.InMemorySignalRepository
import com.example.tradingbot.support.InMemoryTradingState
import com.example.tradingbot.support.NoOpObservabilityPort
import com.example.tradingbot.support.RecordingNotificationPort
import com.example.tradingbot.support.buySignal
import com.example.tradingbot.support.candle
import com.example.tradingbot.support.sellSignal
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProcessClosedCandleServiceTest {

    private val signals = InMemorySignalRepository()
    private val trades = InMemoryPaperTradeRepository()
    private val state = InMemoryTradingState()
    private val notifications = RecordingNotificationPort()

    private val openPaperTrade = mockk<OpenPaperTradeUseCase>(relaxed = true)
    private val closePaperTrade = mockk<ClosePaperTradeUseCase>(relaxed = true)
    private val generateSignal = mockk<GenerateSignalUseCase>(relaxed = true)

    private lateinit var processClosedCandle: ProcessClosedCandleService

    @BeforeEach
    fun setUp() {
        processClosedCandle = ProcessClosedCandleService(
            generateSignal = generateSignal,
            openPaperTrade = openPaperTrade,
            closePaperTrade = closePaperTrade,
            signalRepository = signals,
            tradingStatePort = state,
            paperTradeRepository = trades,
            notifications = NotificationMessageService(notifications),
            evaluator = SignalEvaluator(),
            observability = NoOpObservabilityPort(),
        )
    }

    @Test
    fun `accepts buy signal when no open trade`() {
        val signal = buySignal()
        signals.save(signal)
        every { generateSignal.generate(any()) } returns signal

        processClosedCandle.process(candle())

        assertThat(signals.findById(signal.signalId)!!.status).isEqualTo(SignalStatus.GENERATED)
        assertThat(state.read().marketDataHealthy).isTrue()
    }

    @Test
    fun `ignores buy signal when a trade is already open`() {
        val signal = buySignal()
        signals.save(signal)
        every { generateSignal.generate(any()) } returns signal
        every { openPaperTrade.open(any()) } throws OpenTradeExistsException(BTC)
        state.markOpen(1L, BTC)

        processClosedCandle.process(candle())

        val updated = signals.findById(signal.signalId)!!
        assertThat(updated.status).isEqualTo(SignalStatus.IGNORED)
        assertThat(updated.ignoreReason).isEqualTo(IgnoreReasons.GLOBAL_TRADE_ALREADY_OPEN)
        assertThat(notifications.sent).hasSize(1)
        assertThat(notifications.sent[0]).isInstanceOf(NotificationMessage.IgnoredSignal::class.java)
    }

    @Test
    fun `ignores sell signal when no matching open trade`() {
        val signal = sellSignal()
        signals.save(signal)
        every { generateSignal.generate(any()) } returns signal
        every { closePaperTrade.closeBySellSignal(any()) } returns null

        processClosedCandle.process(candle())

        val updated = signals.findById(signal.signalId)!!
        assertThat(updated.status).isEqualTo(SignalStatus.IGNORED)
        assertThat(updated.ignoreReason).isEqualTo(IgnoreReasons.NO_MATCHING_OPEN_TRADE)
    }

    @Test
    fun `records candle processed even when signal generation skipped`() {
        every { generateSignal.generate(any()) } returns null

        processClosedCandle.process(candle())

        assertThat(state.read().marketDataHealthy).isTrue()
        assertThat(state.read().lastCandleProcessedAt).isNotNull()
    }
}
