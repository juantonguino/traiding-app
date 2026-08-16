package com.example.tradingbot.domain.service

import com.example.tradingbot.domain.model.IgnoreReasons
import com.example.tradingbot.domain.model.SignalSide
import com.example.tradingbot.domain.model.TradingState
import com.example.tradingbot.domain.valueobject.Symbol

sealed class SignalAcceptance {
    object Proceed : SignalAcceptance()
    data class Ignore(val reason: String) : SignalAcceptance()
}

class SignalEvaluator {

    fun assess(side: SignalSide, state: TradingState): SignalAcceptance {
        if (state.emergencyActive) {
            return SignalAcceptance.Ignore(IgnoreReasons.EMERGENCY_ACTIVE)
        }
        if (!state.signalsEnabled) {
            return SignalAcceptance.Ignore(IgnoreReasons.SIGNALS_DISABLED)
        }
        return when (side) {
            SignalSide.BUY -> if (state.hasOpenTrade()) {
                SignalAcceptance.Ignore(IgnoreReasons.GLOBAL_TRADE_ALREADY_OPEN)
            } else {
                SignalAcceptance.Proceed
            }
            SignalSide.SELL -> if (state.hasOpenTrade()) SignalAcceptance.Proceed else {
                SignalAcceptance.Ignore(IgnoreReasons.NO_MATCHING_OPEN_TRADE)
            }
            SignalSide.HOLD -> SignalAcceptance.Proceed
        }
    }
}
