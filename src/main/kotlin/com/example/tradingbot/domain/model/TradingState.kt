package com.example.tradingbot.domain.model

import com.example.tradingbot.domain.valueobject.Symbol
import java.time.Instant

data class TradingState(
    val mode: String,
    val signalsEnabled: Boolean,
    val openTradeId: Long?,
    val openSymbol: Symbol?,
    val emergencyActive: Boolean,
    val marketDataHealthy: Boolean,
    val lastCandleProcessedAt: Instant?,
    val signalsDisabledBy: String?,
    val signalsDisabledAt: Instant?,
) {
    fun hasOpenTrade(): Boolean = openTradeId != null
}

data class StatBucket(
    val closedTrades: Long,
    val netPnl: java.math.BigDecimal,
    val winRatePct: java.math.BigDecimal,
)

data class TradingStatistics(
    val closedTrades: Long,
    val openTrades: Long,
    val accumulatedGrossPnl: java.math.BigDecimal,
    val accumulatedFees: java.math.BigDecimal,
    val accumulatedSlippage: java.math.BigDecimal,
    val accumulatedNetPnl: java.math.BigDecimal,
    val winRatePct: java.math.BigDecimal,
    val averageGain: java.math.BigDecimal,
    val averageLoss: java.math.BigDecimal,
    val maxDrawdown: java.math.BigDecimal,
    val bySymbol: Map<String, StatBucket>,
    val byStrategy: Map<String, StatBucket>,
    val byTimeframe: Map<String, StatBucket>,
)
