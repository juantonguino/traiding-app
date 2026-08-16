package com.example.tradingbot.configuration

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class TradingPropertiesValidator(private val properties: TradingProperties) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(TradingPropertiesValidator::class.java)

    override fun run(args: ApplicationArguments) {
        if (properties.tradingMode != "PAPER") {
            throw IllegalStateException("Refusing to start: TRADING_MODE must be PAPER (got '${properties.tradingMode}'). Real orders are out of scope.")
        }
        if (properties.maxOpenTrades != 1) {
            throw IllegalStateException("Refusing to start: MAX_OPEN_TRADES must be 1 (got ${properties.maxOpenTrades}).")
        }
        if (properties.allowRealOrders) {
            throw IllegalStateException("Refusing to start: ALLOW_REAL_ORDERS must be false. Real order execution is out of scope.")
        }
        if (properties.symbols.isEmpty()) {
            throw IllegalStateException("Refusing to start: at least one TRADING_SYMBOLS must be configured.")
        }
        if (properties.timeframe.isBlank()) {
            throw IllegalStateException("Refusing to start: TRADING_TIMEFRAME must not be blank.")
        }
        if (properties.feePercent.signum() < 0 || properties.slippagePercent.signum() < 0) {
            throw IllegalStateException("Refusing to start: commission and slippage percentages must not be negative.")
        }
        logger.info("TradingProperties validated: mode={}, symbols={}, timeframe={}, strategy={}, entryNotionalUsdt={}",
            properties.tradingMode, properties.symbols, properties.timeframe, properties.strategy, properties.entryNotionalUsdt)
    }
}
