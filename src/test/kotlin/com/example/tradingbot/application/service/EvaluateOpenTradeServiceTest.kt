package com.example.tradingbot.application.service

import com.example.tradingbot.application.port.input.ClosePaperTradeUseCase
import com.example.tradingbot.application.port.output.MarketDataPort
import com.example.tradingbot.domain.model.CloseReason
import com.example.tradingbot.domain.model.PaperTrade
import com.example.tradingbot.domain.valueobject.Price
import com.example.tradingbot.domain.valueobject.Symbol
import com.example.tradingbot.support.EPOCH
import com.example.tradingbot.support.FixedClockPort
import com.example.tradingbot.support.InMemoryPaperTradeRepository
import com.example.tradingbot.support.openTrade
import com.example.tradingbot.support.price
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class EvaluateOpenTradeServiceTest {

    private val trades = InMemoryPaperTradeRepository()
    private val closePaperTrade = mockk<ClosePaperTradeUseCase>(relaxed = true)

    private val config = TradingConfig(
        maxOpenTrades = 1,
        entryNotionalUsdt = BigDecimal("100"),
        feePercent = BigDecimal("0.001"),
        slippagePercent = BigDecimal("0.001"),
        stopLossPercent = null,
        takeProfitPercent = null,
        tradeExpirationSeconds = null,
    )

    private fun service(currentPrice: String): EvaluateOpenTradeService = EvaluateOpenTradeService(
        paperTradeRepository = trades,
        marketDataPort = object : MarketDataPort {
            override fun currentPrice(symbol: Symbol): Price = price(currentPrice)
        },
        closePaperTrade = closePaperTrade,
        clockPort = FixedClockPort(EPOCH.plusSeconds(3600)),
        config = config,
    )

    @Test
    fun `closes by stop loss when price drops below it`() {
        trades.save(openTrade(stopLoss = "59000.0"))
        service(currentPrice = "58000.0").evaluate(EPOCH.plusSeconds(3600))

        verify { closePaperTrade.closeCurrent(CloseReason.STOP_LOSS, price("59000.0"), "scheduler", null) }
    }

    @Test
    fun `closes by take profit when price rises above it`() {
        trades.save(openTrade(takeProfit = "63000.0"))
        service(currentPrice = "64000.0").evaluate(EPOCH.plusSeconds(3600))

        verify { closePaperTrade.closeCurrent(CloseReason.TAKE_PROFIT, price("63000.0"), "scheduler", null) }
    }

    @Test
    fun `does nothing when no open trade`() {
        service(currentPrice = "60000.0").evaluate(EPOCH.plusSeconds(3600))
        verify(exactly = 0) { closePaperTrade.closeCurrent(any(), any(), any(), any()) }
    }

    @Test
    fun `closes by expiration after configured duration`() {
        val configWithExpiry = config.copy(tradeExpirationSeconds = 3600)
        trades.save(openTrade(openTime = EPOCH))

        val svc = EvaluateOpenTradeService(
            paperTradeRepository = trades,
            marketDataPort = object : MarketDataPort {
                override fun currentPrice(symbol: Symbol): Price = price("60000.0")
            },
            closePaperTrade = closePaperTrade,
            clockPort = FixedClockPort(EPOCH.plusSeconds(7200)),
            config = configWithExpiry,
        )

        svc.evaluate(EPOCH.plusSeconds(7200))

        verify { closePaperTrade.closeCurrent(CloseReason.EXPIRATION, "scheduler", null) }
    }

    @Test
    fun `does nothing when price inside stop and take profit bands`() {
        trades.save(openTrade(stopLoss = "59000.0", takeProfit = "63000.0"))
        service(currentPrice = "61000.0").evaluate(EPOCH.plusSeconds(3600))

        verify(exactly = 0) { closePaperTrade.closeCurrent(any(), any(), any(), any()) }
    }
}
