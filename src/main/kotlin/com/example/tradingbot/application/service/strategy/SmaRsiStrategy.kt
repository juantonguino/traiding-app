package com.example.tradingbot.application.service.strategy

import com.example.tradingbot.domain.model.MarketCandle
import com.example.tradingbot.domain.model.SignalSide
import com.example.tradingbot.domain.valueobject.Price
import java.math.BigDecimal
import java.math.RoundingMode
import java.math.MathContext

class SmaRsiStrategy(
    private val config: StrategyConfig,
) : TradingStrategy {

    override val id: String = "sma-rsi"

    override fun evaluate(candle: MarketCandle, history: CandleHistory): SignalDecision {
        history.append(candle)
        val closes = history.closes(candle.symbol, config.smaLongWindow)
        if (closes.size < config.smaLongWindow) {
            return SignalDecision(
                side = SignalSide.HOLD,
                price = candle.close,
                confidence = BigDecimal.ZERO,
                reason = "Not enough history yet (${closes.size}/${config.smaLongWindow})",
                stopLoss = null,
                takeProfit = null,
            )
        }

        val smaShort = sma(closes, config.smaShortWindow)
        val smaLong = sma(closes, config.smaLongWindow)
        val rsi = rsi(closes, config.rsiPeriod)

        val stopLoss = config.stopLossPercent?.let { pct ->
            Price(candle.close.value.multiply(BigDecimal.ONE.subtract(pct)))
        }
        val takeProfit = config.takeProfitPercent?.let { pct ->
            Price(candle.close.value.multiply(BigDecimal.ONE.add(pct)))
        }

        return when {
            smaShort > smaLong && rsi < config.rsiOversold -> SignalDecision(
                side = SignalSide.BUY,
                price = candle.close,
                confidence = confidence(config.rsiOversold, rsi),
                reason = "SMA ${config.smaShortWindow}/${config.smaLongWindow} bullish crossover with RSI $rsi < ${config.rsiOversold}",
                stopLoss = stopLoss,
                takeProfit = takeProfit,
            )
            smaShort < smaLong && rsi > config.rsiOverbought -> SignalDecision(
                side = SignalSide.SELL,
                price = candle.close,
                confidence = confidence(rsi, config.rsiOverbought),
                reason = "SMA ${config.smaShortWindow}/${config.smaLongWindow} bearish crossover with RSI $rsi > ${config.rsiOverbought}",
                stopLoss = stopLoss,
                takeProfit = takeProfit,
            )
            else -> SignalDecision(
                side = SignalSide.HOLD,
                price = candle.close,
                confidence = BigDecimal("50"),
                reason = "No crossover or RSI out of extreme zones (RSI $rsi)",
                stopLoss = stopLoss,
                takeProfit = takeProfit,
            )
        }
    }

    private fun confidence(far: BigDecimal, actual: BigDecimal): BigDecimal {
        val distance = far.subtract(actual).abs()
        val scaled = BigDecimal("50").add(distance.multiply(BigDecimal("2")))
        return scaled.coerceIn(BigDecimal("1"), BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
    }

    private fun sma(values: List<BigDecimal>, window: Int): BigDecimal {
        val slice = values.takeLast(window)
        val sum = slice.fold(BigDecimal.ZERO, BigDecimal::add)
        return sum.divide(BigDecimal(window), MathContext.DECIMAL64)
    }

    private fun rsi(values: List<BigDecimal>, period: Int): BigDecimal {
        val closes = values.takeLast(period + 1)
        if (closes.size < 2) return BigDecimal("50")
        var avgGain = BigDecimal.ZERO
        var avgLoss = BigDecimal.ZERO
        for (i in 1 until closes.size) {
            val change = closes[i].subtract(closes[i - 1])
            if (change.signum() >= 0) {
                avgGain = avgGain.add(change)
            } else {
                avgLoss = avgLoss.add(change.abs())
            }
        }
        val count = closes.size - 1
        avgGain = avgGain.divide(BigDecimal(count), MathContext.DECIMAL64)
        avgLoss = avgLoss.divide(BigDecimal(count), MathContext.DECIMAL64)
        if (avgLoss.signum() == 0) return BigDecimal("100")
        val rs = avgGain.divide(avgLoss, MathContext.DECIMAL64)
        val rsi = BigDecimal("100").subtract(BigDecimal("100").divide(BigDecimal.ONE.add(rs), MathContext.DECIMAL64))
        return rsi.setScale(2, RoundingMode.HALF_UP)
    }
}
