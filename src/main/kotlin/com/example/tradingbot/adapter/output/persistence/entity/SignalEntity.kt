package com.example.tradingbot.adapter.output.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "signals",
    uniqueConstraints = [
        UniqueConstraint(name = "idx_signals_candle", columnNames = ["symbol", "timeframe", "candle_open_time", "strategy"]),
    ],
)
class SignalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "signal_id", nullable = false, unique = true, length = 36)
    var signalId: String? = null

    @Column(name = "symbol", nullable = false, length = 20)
    var symbol: String? = null

    @Column(name = "timeframe", nullable = false, length = 10)
    var timeframe: String? = null

    @Column(name = "side", nullable = false, length = 10)
    var side: String? = null

    @Column(name = "price", nullable = false, precision = 24, scale = 8)
    var price: BigDecimal? = null

    @Column(name = "candle_open_time", nullable = false)
    var candleOpenTime: Instant? = null

    @Column(name = "candle_close_time", nullable = false)
    var candleCloseTime: Instant? = null

    @Column(name = "strategy", nullable = false, length = 50)
    var strategy: String? = null

    @Column(name = "confidence", precision = 5, scale = 2)
    var confidence: BigDecimal? = null

    @Column(name = "reason", nullable = false, length = 255)
    var reason: String? = null

    @Column(name = "stop_loss", precision = 24, scale = 8)
    var stopLoss: BigDecimal? = null

    @Column(name = "take_profit", precision = 24, scale = 8)
    var takeProfit: BigDecimal? = null

    @Column(name = "status", nullable = false, length = 20)
    var status: String? = null

    @Column(name = "ignore_reason", length = 50)
    var ignoreReason: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "open_trade_id")
    var openTrade: PaperTradeEntity? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
}
