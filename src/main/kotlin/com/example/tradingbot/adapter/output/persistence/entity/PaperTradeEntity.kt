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
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "paper_trades")
class PaperTradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "trade_id", nullable = false, unique = true, length = 36)
    var tradeId: String? = null

    @Column(name = "symbol", nullable = false, length = 20)
    var symbol: String? = null

    @Column(name = "timeframe", nullable = false, length = 10)
    var timeframe: String? = null

    @Column(name = "strategy", nullable = false, length = 50)
    var strategy: String? = null

    @Column(name = "quantity", nullable = false, precision = 24, scale = 8)
    var quantity: BigDecimal? = null

    @Column(name = "entry_price", nullable = false, precision = 24, scale = 8)
    var entryPrice: BigDecimal? = null

    @Column(name = "entry_notional", nullable = false, precision = 24, scale = 8)
    var entryNotional: BigDecimal? = null

    @Column(name = "open_time", nullable = false)
    var openTime: Instant? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "open_signal_id", nullable = false)
    var openSignal: SignalEntity? = null

    @Column(name = "stop_loss", precision = 24, scale = 8)
    var stopLoss: BigDecimal? = null

    @Column(name = "take_profit", precision = 24, scale = 8)
    var takeProfit: BigDecimal? = null

    @Column(name = "status", nullable = false, length = 10)
    var status: String? = null

    @Column(name = "open_guard", insertable = false, updatable = false)
    var openGuard: String? = null

    @Column(name = "close_reason", length = 30)
    var closeReason: String? = null

    @Column(name = "exit_price", precision = 24, scale = 8)
    var exitPrice: BigDecimal? = null

    @Column(name = "close_time")
    var closeTime: Instant? = null

    @Column(name = "duration_seconds")
    var durationSeconds: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "close_signal_id")
    var closeSignal: SignalEntity? = null

    @Column(name = "gross_pnl", precision = 24, scale = 8)
    var grossPnl: BigDecimal? = null

    @Column(name = "fees", precision = 24, scale = 8)
    var fees: BigDecimal? = null

    @Column(name = "slippage_cost", precision = 24, scale = 8)
    var slippageCost: BigDecimal? = null

    @Column(name = "net_pnl", precision = 24, scale = 8)
    var netPnl: BigDecimal? = null

    @Column(name = "return_pct", precision = 12, scale = 4)
    var returnPct: BigDecimal? = null

    @Column(name = "result", length = 12)
    var result: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
}
