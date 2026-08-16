package com.example.tradingbot.application.service

import com.example.tradingbot.application.port.input.EvaluateOpenTradeUseCase
import com.example.tradingbot.application.port.output.ClockPort
import com.example.tradingbot.application.port.output.MarketDataPort
import com.example.tradingbot.application.port.output.PaperTradeRepositoryPort
import com.example.tradingbot.application.port.input.ClosePaperTradeUseCase
import com.example.tradingbot.domain.model.CloseReason
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

class EvaluateOpenTradeService(
    private val paperTradeRepository: PaperTradeRepositoryPort,
    private val marketDataPort: MarketDataPort,
    private val closePaperTrade: ClosePaperTradeUseCase,
    private val clockPort: ClockPort,
    private val config: TradingConfig,
) : EvaluateOpenTradeUseCase {

    private val logger = LoggerFactory.getLogger(EvaluateOpenTradeService::class.java)

    override fun evaluate(now: Instant) {
        val open = paperTradeRepository.findOpenTrade() ?: return

        config.tradeExpirationSeconds?.let { expiration ->
            if (Duration.between(open.openTime, now).seconds >= expiration) {
                logger.info("Closing trade {} by EXPIRATION (open {} > {}s)",
                    open.tradeId.value, open.openTime, expiration)
                closePaperTrade.closeCurrent(CloseReason.EXPIRATION, actor = "scheduler")
                return
            }
        }

        val price = marketDataPort.currentPrice(open.symbol)
        when {
            open.stopLoss != null && price.value <= open.stopLoss.value -> {
                logger.info("Closing trade {} by STOP_LOSS at {} (SL {})",
                    open.tradeId.value, price.value, open.stopLoss.value)
                closePaperTrade.closeCurrent(CloseReason.STOP_LOSS, open.stopLoss, actor = "scheduler")
            }
            open.takeProfit != null && price.value >= open.takeProfit.value -> {
                logger.info("Closing trade {} by TAKE_PROFIT at {} (TP {})",
                    open.tradeId.value, price.value, open.takeProfit.value)
                closePaperTrade.closeCurrent(CloseReason.TAKE_PROFIT, open.takeProfit, actor = "scheduler")
            }
        }
    }
}
