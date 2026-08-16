package com.example.tradingbot.domain.model

enum class SignalSide {
    BUY,
    SELL,
    HOLD,
}

enum class SignalStatus {
    GENERATED,
    ACCEPTED,
    IGNORED,
    USED_TO_OPEN,
    USED_TO_CLOSE,
    EXPIRED,
}

object IgnoreReasons {
    const val GLOBAL_TRADE_ALREADY_OPEN = "GLOBAL_TRADE_ALREADY_OPEN"
    const val NO_MATCHING_OPEN_TRADE = "NO_MATCHING_OPEN_TRADE"
    const val SIGNALS_DISABLED = "SIGNALS_DISABLED"
    const val EMERGENCY_ACTIVE = "EMERGENCY_ACTIVE"
}

enum class TradeStatus {
    OPEN,
    CLOSED,
}

enum class TradeResult {
    WIN,
    LOSS,
    BREAK_EVEN,
}

enum class CloseReason {
    SELL_SIGNAL,
    STOP_LOSS,
    TAKE_PROFIT,
    MANUAL_CLOSE,
    EMERGENCY,
    EXPIRATION,
}
