package com.example.tradingbot.domain.service

import com.example.tradingbot.domain.model.IgnoreReasons
import com.example.tradingbot.domain.model.SignalSide
import com.example.tradingbot.domain.model.TradingState
import com.example.tradingbot.support.BTC
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SignalEvaluatorTest {

    private val evaluator = SignalEvaluator()

    private fun state(
        signalsEnabled: Boolean = true,
        emergencyActive: Boolean = false,
        hasOpen: Boolean = false,
    ) = TradingState(
        mode = "PAPER",
        signalsEnabled = signalsEnabled,
        openTradeId = if (hasOpen) 1L else null,
        openSymbol = if (hasOpen) BTC else null,
        emergencyActive = emergencyActive,
        marketDataHealthy = true,
        lastCandleProcessedAt = Instant.EPOCH,
        signalsDisabledBy = null,
        signalsDisabledAt = null,
    )

    @Test
    fun `buy proceeds when no open trade`() {
        assertThat(evaluator.assess(SignalSide.BUY, state(hasOpen = false)))
            .isEqualTo(SignalAcceptance.Proceed)
    }

    @Test
    fun `buy ignored when a trade is already open`() {
        assertThat(evaluator.assess(SignalSide.BUY, state(hasOpen = true)))
            .isEqualTo(SignalAcceptance.Ignore(IgnoreReasons.GLOBAL_TRADE_ALREADY_OPEN))
    }

    @Test
    fun `sell ignored when no matching open trade`() {
        assertThat(evaluator.assess(SignalSide.SELL, state(hasOpen = false)))
            .isEqualTo(SignalAcceptance.Ignore(IgnoreReasons.NO_MATCHING_OPEN_TRADE))
    }

    @Test
    fun `sell proceeds when open trade exists`() {
        assertThat(evaluator.assess(SignalSide.SELL, state(hasOpen = true)))
            .isEqualTo(SignalAcceptance.Proceed)
    }

    @Test
    fun `buy ignored when signals disabled`() {
        assertThat(evaluator.assess(SignalSide.BUY, state(signalsEnabled = false)))
            .isEqualTo(SignalAcceptance.Ignore(IgnoreReasons.SIGNALS_DISABLED))
    }

    @Test
    fun `everything ignored during emergency`() {
        assertThat(evaluator.assess(SignalSide.SELL, state(emergencyActive = true)))
            .isEqualTo(SignalAcceptance.Ignore(IgnoreReasons.EMERGENCY_ACTIVE))
    }
}
