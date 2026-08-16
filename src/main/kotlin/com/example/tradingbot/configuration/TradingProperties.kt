package com.example.tradingbot.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal
import java.time.Duration

@ConfigurationProperties(prefix = "trading")
class TradingProperties {
    var tradingMode: String = "PAPER"
    var maxOpenTrades: Int = 1
    var allowRealOrders: Boolean = false

    var symbols: List<String> = listOf("BTCUSDT")
    var timeframe: String = "15m"
    var strategy: String = "sma-rsi"
    var entryNotionalUsdt: BigDecimal = BigDecimal("100.00")
    var feePercent: BigDecimal = BigDecimal("0.001")
    var slippagePercent: BigDecimal = BigDecimal("0.001")
    var stopLossPercent: BigDecimal? = null
    var takeProfitPercent: BigDecimal? = null
    var tradeExpirationSeconds: Long? = null

    var smaShortWindow: Int = 20
    var smaLongWindow: Int = 50
    var rsiPeriod: Int = 14
    var rsiOversold: BigDecimal = BigDecimal("30")
    var rsiOverbought: BigDecimal = BigDecimal("70")

    var evaluationIntervalSeconds: Long = 60

    var binance = Binance()
    var telegram = Telegram()
    var summary = Summary()
    var scheduling = Scheduling()

    class Binance {
        var streamEnabled: Boolean = true
        var wsBaseUrl: String = "wss://stream.binance.com:9443"
        var restBaseUrl: String = "https://api.binance.com"
        var connectTimeout: Duration = Duration.ofSeconds(10)
        var reconnectMaxBackoff: Duration = Duration.ofSeconds(60)
        var dataAbsenceWindow: Duration = Duration.ofMinutes(5)
    }

    class Telegram {
        var apiBaseUrl: String = "https://api.telegram.org"
        var token: String? = null
        var chatId: String? = null
        var timeout: Duration = Duration.ofSeconds(10)
        var maxRetries: Int = 2
        var enabled: Boolean = true
    }

    class Summary {
        var cron: String = "0 0 0 * * *"
    }

    class Scheduling {
        var enabled: Boolean = true
    }
}
