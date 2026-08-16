package com.example.tradingbot.application.port.output

import java.math.BigDecimal

interface ObservabilityPort {
    fun candleProcessed()

    fun signalGenerated()

    fun signalIgnored(reason: String)

    fun tradeOpened()

    fun tradeClosed()

    fun recordNetPnl(netPnl: BigDecimal)

    fun adapterError(adapter: String, operation: String)

    fun marketDataHealthy(healthy: Boolean)
}
