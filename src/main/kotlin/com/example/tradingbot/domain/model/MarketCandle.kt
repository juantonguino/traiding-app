package com.example.tradingbot.domain.model

import com.example.tradingbot.domain.valueobject.Price
import com.example.tradingbot.domain.valueobject.SignalId
import com.example.tradingbot.domain.valueobject.Symbol
import com.example.tradingbot.domain.valueobject.Timeframe
import java.math.BigDecimal
import java.time.Instant

data class MarketCandle(
    val symbol: Symbol,
    val timeframe: Timeframe,
    val openTime: Instant,
    val closeTime: Instant,
    val open: Price,
    val close: Price,
    val high: Price,
    val low: Price,
    val volume: BigDecimal,
    val isClosed: Boolean,
) {
    init {
        require(openTime.isBefore(closeTime) || openTime == closeTime) { "Candle openTime must be <= closeTime" }
        require(low.value <= high.value) { "Candle low must be <= high" }
        require(volume.signum() >= 0) { "Candle volume must not be negative" }
    }
}

data class TradingSignal(
    val signalId: SignalId,
    val symbol: Symbol,
    val timeframe: Timeframe,
    val side: SignalSide,
    val price: Price,
    val candleOpenTime: Instant,
    val candleCloseTime: Instant,
    val strategy: String,
    val confidence: BigDecimal?,
    val reason: String,
    val stopLoss: Price?,
    val takeProfit: Price?,
    val status: SignalStatus,
    val ignoreReason: String?,
    val createdAt: Instant,
) {
    fun asAccepted(): TradingSignal = copy(status = SignalStatus.ACCEPTED)

    fun asIgnored(reason: String): TradingSignal = copy(status = SignalStatus.IGNORED, ignoreReason = reason)

    fun asUsedToOpen(): TradingSignal = copy(status = SignalStatus.USED_TO_OPEN)

    fun asUsedToClose(): TradingSignal = copy(status = SignalStatus.USED_TO_CLOSE)

    fun asExpired(): TradingSignal = copy(status = SignalStatus.EXPIRED)
}
