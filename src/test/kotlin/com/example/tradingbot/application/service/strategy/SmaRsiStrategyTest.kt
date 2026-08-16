package com.example.tradingbot.application.service.strategy

import com.example.tradingbot.domain.model.MarketCandle
import com.example.tradingbot.domain.model.SignalSide
import com.example.tradingbot.domain.valueobject.Price
import com.example.tradingbot.domain.valueobject.Symbol
import com.example.tradingbot.domain.valueobject.Timeframe
import com.example.tradingbot.support.BTC
import com.example.tradingbot.support.M15
import com.example.tradingbot.support.price
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class SmaRsiStrategyTest {

    private val config = StrategyConfig(
        smaShortWindow = 5,
        smaLongWindow = 10,
        rsiPeriod = 3,
        rsiOversold = BigDecimal("30"),
        rsiOverbought = BigDecimal("70"),
        stopLossPercent = BigDecimal("0.02"),
        takeProfitPercent = BigDecimal("0.03"),
    )

    private val strategy = SmaRsiStrategy(config)
    private val history = InMemoryCandleHistory()

    private fun pushCloses(vararg closes: String) {
        closes.forEachIndexed { i, close ->
            val t = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i * 900L)
            history.append(
                MarketCandle(
                    symbol = BTC,
                    timeframe = M15,
                    openTime = t,
                    closeTime = t.plusSeconds(900),
                    open = price(close),
                    close = price(close),
                    high = price(close),
                    low = price(close),
                    volume = BigDecimal.ONE,
                    isClosed = true,
                ),
            )
        }
    }

    @Test
    fun `holds until enough history is available`() {
        pushCloses("100", "101", "102", "103", "104")

        val decision = strategy.evaluate(
            MarketCandle(BTC, M15, Instant.parse("2026-01-01T00:45:00Z"), Instant.parse("2026-01-01T01:00:00Z"),
                price("105"), price("105"), price("105"), price("105"), BigDecimal.ONE, true),
            history,
        )

        assertThat(decision.side).isEqualTo(SignalSide.HOLD)
        assertThat(decision.reason).contains("Not enough history")
    }

    @Test
    fun `buys on short-term uptrend with oversold rsi`() {
        pushCloses("100", "105", "110", "115", "120", "125", "130", "128", "124", "120")

        val decision = strategy.evaluate(
            MarketCandle(BTC, M15, Instant.parse("2026-01-01T02:30:00Z"), Instant.parse("2026-01-01T02:45:00Z"),
                price("118"), price("118"), price("118"), price("118"), BigDecimal.ONE, true),
            history,
        )

        assertThat(decision.side).isEqualTo(SignalSide.BUY)
        assertThat(decision.confidence).isBetween(BigDecimal.ONE, BigDecimal("100"))
        assertThat(decision.stopLoss).isNotNull()
        assertThat(decision.takeProfit).isNotNull()
    }

    @Test
    fun `sells on short-term downtrend with overbought rsi`() {
        pushCloses("100", "110", "120", "130", "140", "132", "126", "118", "120", "124")

        val decision = strategy.evaluate(
            MarketCandle(BTC, M15, Instant.parse("2026-01-01T02:30:00Z"), Instant.parse("2026-01-01T02:45:00Z"),
                price("128"), price("128"), price("128"), price("128"), BigDecimal.ONE, true),
            history,
        )

        assertThat(decision.side).isEqualTo(SignalSide.SELL)
    }

    @Test
    fun `deduplicates candles with identical open time`() {
        pushCloses("100", "101", "102")
        val candle = MarketCandle(BTC, M15, Instant.parse("2026-01-01T00:45:00Z"), Instant.parse("2026-01-01T01:00:00Z"),
            price("103"), price("103"), price("103"), price("103"), BigDecimal.ONE, true)
        history.append(candle)
        history.append(candle)

        assertThat(history.size(BTC)).isEqualTo(4)
    }

    @Test
    fun `strategy id is sma-rsi`() {
        assertThat(strategy.id).isEqualTo("sma-rsi")
    }
}
