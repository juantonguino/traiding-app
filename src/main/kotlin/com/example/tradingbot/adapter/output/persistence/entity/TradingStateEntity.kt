package com.example.tradingbot.adapter.output.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "trading_state")
class TradingStateEntity {

    @Id
    var id: Long = 1L

    @Column(name = "mode", nullable = false, length = 10)
    var mode: String? = null

    @Column(name = "signals_enabled", nullable = false)
    var signalsEnabled: Boolean? = null

    @Column(name = "open_trade_id")
    var openTradeId: Long? = null

    @Column(name = "open_symbol", length = 20)
    var openSymbol: String? = null

    @Column(name = "emergency_active", nullable = false)
    var emergencyActive: Boolean? = null

    @Column(name = "market_data_healthy", nullable = false)
    var marketDataHealthy: Boolean? = null

    @Column(name = "last_candle_processed_at")
    var lastCandleProcessedAt: Instant? = null

    @Column(name = "signals_disabled_by", length = 50)
    var signalsDisabledBy: String? = null

    @Column(name = "signals_disabled_at")
    var signalsDisabledAt: Instant? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
}
