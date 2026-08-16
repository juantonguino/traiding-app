package com.example.tradingbot.support

import com.example.tradingbot.domain.model.MarketCandle
import com.example.tradingbot.domain.model.PaperTrade
import com.example.tradingbot.domain.model.SignalSide
import com.example.tradingbot.domain.model.SignalStatus
import com.example.tradingbot.domain.model.TradingSignal
import com.example.tradingbot.domain.valueobject.Money
import com.example.tradingbot.domain.valueobject.Price
import com.example.tradingbot.domain.valueobject.Quantity
import com.example.tradingbot.domain.valueobject.SignalId
import com.example.tradingbot.domain.valueobject.Symbol
import com.example.tradingbot.domain.valueobject.Timeframe
import com.example.tradingbot.domain.valueobject.TradeId
import java.math.BigDecimal
import java.time.Instant

val BTC = Symbol("BTCUSDT")
val M15 = Timeframe("15m")
val EPOCH: Instant = Instant.parse("2026-01-01T00:00:00Z")

fun price(raw: String): Price = Price(BigDecimal(raw))

fun qty(raw: String): Quantity = Quantity(BigDecimal(raw))

fun candle(
    openTime: Instant = EPOCH,
    open: String = "60000.0",
    close: String = "60500.0",
    high: String = "61000.0",
    low: String = "59500.0",
): MarketCandle = MarketCandle(
    symbol = BTC,
    timeframe = M15,
    openTime = openTime,
    closeTime = openTime.plusSeconds(900),
    open = price(open),
    close = price(close),
    high = price(high),
    low = price(low),
    volume = BigDecimal("1.5"),
    isClosed = true,
)

fun buySignal(
    price: String = "60500.0",
    candleOpenTime: Instant = EPOCH,
    signalId: String = "sig-buy-1",
): TradingSignal = TradingSignal(
    signalId = SignalId(signalId),
    symbol = BTC,
    timeframe = M15,
    side = SignalSide.BUY,
    price = price(price),
    candleOpenTime = candleOpenTime,
    candleCloseTime = candleOpenTime.plusSeconds(900),
    strategy = "sma-rsi",
    confidence = BigDecimal("85"),
    reason = "bullish crossover",
    stopLoss = price("59000.0"),
    takeProfit = price("63000.0"),
    status = SignalStatus.GENERATED,
    ignoreReason = null,
    createdAt = EPOCH,
)

fun sellSignal(
    price: String = "62000.0",
    candleOpenTime: Instant = EPOCH.plusSeconds(900),
    signalId: String = "sig-sell-1",
): TradingSignal = TradingSignal(
    signalId = SignalId(signalId),
    symbol = BTC,
    timeframe = M15,
    side = SignalSide.SELL,
    price = price(price),
    candleOpenTime = candleOpenTime,
    candleCloseTime = candleOpenTime.plusSeconds(900),
    strategy = "sma-rsi",
    confidence = BigDecimal("80"),
    reason = "bearish crossover",
    stopLoss = null,
    takeProfit = null,
    status = SignalStatus.GENERATED,
    ignoreReason = null,
    createdAt = candleOpenTime,
)

fun openTrade(
    tradeId: String = "trade-1",
    entry: String = "60500.0",
    quantity: String = "0.00165289",
    openTime: Instant = EPOCH,
    stopLoss: String? = "59000.0",
    takeProfit: String? = "63000.0",
): PaperTrade = PaperTrade.open(
    tradeId = TradeId(tradeId),
    symbol = BTC,
    timeframe = M15,
    strategy = "sma-rsi",
    quantity = qty(quantity),
    entryPrice = price(entry),
    entryNotional = Money(BigDecimal("100.00000000")),
    openTime = openTime,
    openSignalId = SignalId("sig-buy-1"),
    stopLoss = stopLoss?.let(::price),
    takeProfit = takeProfit?.let(::price),
)
