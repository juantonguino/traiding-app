package com.example.tradingbot.application.service.strategy

import com.example.tradingbot.domain.model.MarketCandle
import com.example.tradingbot.domain.model.SignalSide
import com.example.tradingbot.domain.valueobject.Price
import java.math.BigDecimal

data class SignalDecision(
    val side: SignalSide,
    val price: Price,
    val confidence: BigDecimal,
    val reason: String,
    val stopLoss: Price?,
    val takeProfit: Price?,
)

interface TradingStrategy {
    val id: String

    fun evaluate(candle: MarketCandle, history: CandleHistory): SignalDecision
}

data class StrategyConfig(
    val smaShortWindow: Int,
    val smaLongWindow: Int,
    val rsiPeriod: Int,
    val rsiOversold: BigDecimal,
    val rsiOverbought: BigDecimal,
    val stopLossPercent: BigDecimal?,
    val takeProfitPercent: BigDecimal?,
)

interface CandleHistory {
    fun append(candle: MarketCandle)

    fun closes(symbol: com.example.tradingbot.domain.valueobject.Symbol, window: Int): List<BigDecimal>

    fun size(symbol: com.example.tradingbot.domain.valueobject.Symbol): Int
}
