package com.example.tradingbot.application.port.output

import com.example.tradingbot.domain.model.CloseReason
import com.example.tradingbot.domain.service.TradeResultCalculator
import com.example.tradingbot.support.EPOCH
import com.example.tradingbot.support.buySignal
import com.example.tradingbot.support.openTrade
import com.example.tradingbot.support.price
import com.example.tradingbot.support.sellSignal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class NotificationMessageTest {

    private val paperLabel = "PAPER TRADING"

    @Test
    fun `buy signal includes required fields and paper label`() {
        val text = NotificationMessage.BuySignal(buySignal()).text()

        assertThat(text)
            .contains("BUY SIGNAL")
            .contains("BTCUSDT")
            .contains("60500.0")
            .contains("15m")
            .contains("sma-rsi")
            .contains("85%")
            .contains("bullish crossover")
            .contains(paperLabel)
    }

    @Test
    fun `sell signal includes required fields and paper label`() {
        val text = NotificationMessage.SellSignal(sellSignal()).text()

        assertThat(text)
            .contains("SELL SIGNAL")
            .contains("BTCUSDT")
            .contains("62000.0")
            .contains(paperLabel)
    }

    @Test
    fun `ignored signal includes reason open trade and paper label`() {
        val text = NotificationMessage.IgnoredSignal(
            buySignal().asIgnored("GLOBAL_TRADE_ALREADY_OPEN"),
            openSymbol = price("61500.0").let { com.example.tradingbot.domain.valueobject.Symbol("BTCUSDT") },
        ).text()

        assertThat(text)
            .contains("SIGNAL IGNORED")
            .contains("GLOBAL_TRADE_ALREADY_OPEN")
            .contains("Open trade on: Symbol(value=BTCUSDT)")
            .contains(paperLabel)
    }

    @Test
    fun `ignored signal without open trade omits the open trade line`() {
        val text = NotificationMessage.IgnoredSignal(
            buySignal().asIgnored("NO_MATCHING_OPEN_TRADE"),
            openSymbol = null,
        ).text()

        assertThat(text)
            .contains("SIGNAL IGNORED")
            .contains("NO_MATCHING_OPEN_TRADE")
            .doesNotContain("Open trade on:")
            .contains(paperLabel)
    }

    @Test
    fun `trade opened includes required fields and paper label`() {
        val text = NotificationMessage.TradeOpened(openTrade()).text()

        assertThat(text)
            .contains("TRADE OPENED")
            .contains("BTCUSDT")
            .contains("60500.0")
            .contains("0.00165289")
            .contains("Stop-loss: 59000.0")
            .contains("Take-profit: 63000.0")
            .contains("2026-01-01 00:00:00 UTC")
            .contains(paperLabel)
    }

    @Test
    fun `trade closed includes exit close reason net pnl and paper label`() {
        val closed = closeTrade()

        val text = NotificationMessage.TradeClosed(closed).text()

        assertThat(text)
            .contains("TRADE CLOSED")
            .contains("BTCUSDT")
            .contains("Exit: 63000.0")
            .contains("Close reason: TAKE_PROFIT")
            .contains("Net PnL: 3.72396117")
            .contains("Return: 3.7200%")
            .contains("Duration: 3600s")
            .contains(paperLabel)
    }

    @Test
    fun `gain or loss result includes result net pnl and paper label`() {
        val text = NotificationMessage.GainOrLoss(closeTrade()).text()

        assertThat(text)
            .contains("RESULT: WIN")
            .contains("Net PnL: 3.72396117")
            .contains("3.7200%")
            .contains(paperLabel)
    }

    @Test
    fun `critical error includes message and paper label`() {
        val text = NotificationMessage.CriticalError("binance rest failed").text()

        assertThat(text)
            .contains("CRITICAL ERROR")
            .contains("binance rest failed")
            .contains(paperLabel)
    }

    @Test
    fun `market data loss includes symbol and paper label`() {
        val text = NotificationMessage.MarketDataLoss(com.example.tradingbot.domain.valueobject.Symbol("BTCUSDT")).text()

        assertThat(text)
            .contains("MARKET DATA LOSS")
            .contains("BTCUSDT")
            .contains(paperLabel)
    }

    @Test
    fun `emergency includes closed trade id and paper label`() {
        val text = NotificationMessage.Emergency("trade-1").text()

        assertThat(text)
            .contains("EMERGENCY STOP ACTIVATED")
            .contains("Open trade closed: trade-1")
            .contains(paperLabel)
    }

    @Test
    fun `summary includes its text and paper label`() {
        val text = NotificationMessage.Summary("2 trades, net +5.00 USDT").text()

        assertThat(text)
            .contains("PERIODIC RESULT SUMMARY")
            .contains("2 trades, net +5.00 USDT")
            .contains(paperLabel)
    }

    @Test
    fun `correlation ids default to signal trade or system identifiers`() {
        assertThat(NotificationMessage.BuySignal(buySignal(signalId = "sig-buy-1")).correlationId).isEqualTo("sig-buy-1")
        assertThat(NotificationMessage.TradeOpened(openTrade(tradeId = "trade-1")).correlationId).isEqualTo("trade-1")
        assertThat(NotificationMessage.CriticalError("x").correlationId).isEqualTo("system")
        assertThat(NotificationMessage.Emergency(null).correlationId).isEqualTo("system")
    }

    private fun closeTrade() = openTrade().closedWith(
        exitPrice = price("63000.0"),
        closeTime = EPOCH.plusSeconds(3600),
        closeReason = CloseReason.TAKE_PROFIT,
        computation = TradeResultCalculator(BigDecimal("0.001"), BigDecimal("0.001"))
            .compute(price("60500.0"), price("63000.0"), openTrade().quantity),
        closeSignalId = com.example.tradingbot.domain.valueobject.SignalId("sig-sell-1"),
    )
}
