package com.example.tradingbot.adapter.output.health

import com.example.tradingbot.adapter.output.telegram.TelegramNotificationAdapter
import com.example.tradingbot.application.port.output.MarketDataConnectionPort
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component
import javax.sql.DataSource

@Component("mysqlHealthIndicator")
class MySqlHealthIndicator(private val dataSource: DataSource) : HealthIndicator {
    override fun health(): Health = try {
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.execute("SELECT 1") }
        }
        Health.up().build()
    } catch (e: Exception) {
        Health.down(e).build()
    }
}

@Component("binanceHealthIndicator")
class BinanceHealthIndicator(private val connection: MarketDataConnectionPort) : HealthIndicator {
    override fun health(): Health =
        if (connection.isConnected()) Health.up().build()
        else Health.down().withDetail("reason", "WebSocket not connected").build()
}

@Component("telegramHealthIndicator")
class TelegramHealthIndicator(private val adapter: TelegramNotificationAdapter) : HealthIndicator {
    override fun health(): Health = adapter.health()
}
