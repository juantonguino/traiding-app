package com.example.tradingbot.application.service

import java.math.BigDecimal

data class TradingConfig(
    val maxOpenTrades: Int,
    val entryNotionalUsdt: BigDecimal,
    val feePercent: BigDecimal,
    val slippagePercent: BigDecimal,
    val stopLossPercent: BigDecimal?,
    val takeProfitPercent: BigDecimal?,
    val tradeExpirationSeconds: Long?,
)
