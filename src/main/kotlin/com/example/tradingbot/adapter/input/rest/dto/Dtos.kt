package com.example.tradingbot.adapter.input.rest.dto

import tools.jackson.databind.annotation.JsonSerialize
import tools.jackson.databind.ser.std.ToStringSerializer
import com.example.tradingbot.domain.model.PaperTrade
import com.example.tradingbot.domain.model.TradingSignal
import com.example.tradingbot.domain.model.TradingStatistics
import java.math.BigDecimal
import java.time.Instant

data class HealthResponse(
    val status: String,
    val checks: Map<String, String>,
)

data class StatusResponse(
    val mode: String,
    val signalsEnabled: Boolean,
    val emergencyActive: Boolean,
    val openTrade: OpenTradeResponse?,
    val marketDataHealthy: Boolean,
    val lastCandleProcessedAt: Instant?,
    val signalsDisabledBy: String?,
    val signalsDisabledAt: Instant?,
)

data class OpenTradeResponse(
    val tradeId: String,
    val symbol: String,
    val timeframe: String,
    val strategy: String,
    @JsonSerialize(using = ToStringSerializer::class)
    val quantity: BigDecimal,
    @JsonSerialize(using = ToStringSerializer::class)
    val entryPrice: BigDecimal,
    val openTime: Instant,
    @JsonSerialize(using = ToStringSerializer::class)
    val stopLoss: BigDecimal?,
    @JsonSerialize(using = ToStringSerializer::class)
    val takeProfit: BigDecimal?,
    val status: String,
    val openSignalId: String,
)

data class TradeResponse(
    val tradeId: String,
    val symbol: String,
    val timeframe: String,
    val strategy: String,
    @JsonSerialize(using = ToStringSerializer::class)
    val quantity: BigDecimal,
    @JsonSerialize(using = ToStringSerializer::class)
    val entryPrice: BigDecimal,
    @JsonSerialize(using = ToStringSerializer::class)
    val exitPrice: BigDecimal?,
    val openTime: Instant,
    val closeTime: Instant?,
    val durationSeconds: Long?,
    val closeReason: String?,
    @JsonSerialize(using = ToStringSerializer::class)
    val grossPnl: BigDecimal?,
    @JsonSerialize(using = ToStringSerializer::class)
    val fees: BigDecimal?,
    @JsonSerialize(using = ToStringSerializer::class)
    val slippageCost: BigDecimal?,
    @JsonSerialize(using = ToStringSerializer::class)
    val netPnl: BigDecimal?,
    @JsonSerialize(using = ToStringSerializer::class)
    val returnPct: BigDecimal?,
    val result: String?,
    val status: String,
    val openSignalId: String,
    val closeSignalId: String?,
)

data class TradesResponse(
    val items: List<TradeResponse>,
    val total: Long,
)

data class SignalResponse(
    val signalId: String,
    val symbol: String,
    val timeframe: String,
    val side: String,
    @JsonSerialize(using = ToStringSerializer::class)
    val price: BigDecimal,
    val candleOpenTime: Instant,
    val strategy: String,
    val confidence: BigDecimal?,
    val reason: String,
    @JsonSerialize(using = ToStringSerializer::class)
    val stopLoss: BigDecimal?,
    @JsonSerialize(using = ToStringSerializer::class)
    val takeProfit: BigDecimal?,
    val status: String,
    val ignoreReason: String?,
)

data class SignalsResponse(
    val items: List<SignalResponse>,
    val total: Long,
)

data class StatBucketResponse(
    val closedTrades: Long,
    @JsonSerialize(using = ToStringSerializer::class)
    val netPnl: BigDecimal,
    @JsonSerialize(using = ToStringSerializer::class)
    val winRatePct: BigDecimal,
)

data class StatisticsResponse(
    val closedTrades: Long,
    val openTrades: Long,
    @JsonSerialize(using = ToStringSerializer::class)
    val accumulatedGrossPnl: BigDecimal,
    @JsonSerialize(using = ToStringSerializer::class)
    val accumulatedFees: BigDecimal,
    @JsonSerialize(using = ToStringSerializer::class)
    val accumulatedSlippage: BigDecimal,
    @JsonSerialize(using = ToStringSerializer::class)
    val accumulatedNetPnl: BigDecimal,
    @JsonSerialize(using = ToStringSerializer::class)
    val winRatePct: BigDecimal,
    @JsonSerialize(using = ToStringSerializer::class)
    val averageGain: BigDecimal,
    @JsonSerialize(using = ToStringSerializer::class)
    val averageLoss: BigDecimal,
    @JsonSerialize(using = ToStringSerializer::class)
    val maxDrawdown: BigDecimal,
    val bySymbol: Map<String, StatBucketResponse>,
    val byStrategy: Map<String, StatBucketResponse>,
    val byTimeframe: Map<String, StatBucketResponse>,
)

data class ErrorResponse(
    val timestamp: Instant,
    val status: Int,
    val code: String,
    val message: String,
    val requestId: String?,
)

data class DisableSignalsRequest(
    val actor: String?,
)

data class ManualCloseRequest(
    val reason: String = "MANUAL_CLOSE",
    val actor: String?,
)

data class EmergencyStopResponse(
    val status: String,
    val closedTradeId: String?,
)

object DtoMappers {
    fun toOpenTrade(trade: PaperTrade): OpenTradeResponse = OpenTradeResponse(
        tradeId = trade.tradeId.value,
        symbol = trade.symbol.value,
        timeframe = trade.timeframe.value,
        strategy = trade.strategy,
        quantity = trade.quantity.value,
        entryPrice = trade.entryPrice.value,
        openTime = trade.openTime,
        stopLoss = trade.stopLoss?.value,
        takeProfit = trade.takeProfit?.value,
        status = trade.status.name,
        openSignalId = trade.openSignalId.value,
    )

    fun toTrade(trade: PaperTrade): TradeResponse = TradeResponse(
        tradeId = trade.tradeId.value,
        symbol = trade.symbol.value,
        timeframe = trade.timeframe.value,
        strategy = trade.strategy,
        quantity = trade.quantity.value,
        entryPrice = trade.entryPrice.value,
        exitPrice = trade.exitPrice?.value,
        openTime = trade.openTime,
        closeTime = trade.closeTime,
        durationSeconds = trade.durationSeconds,
        closeReason = trade.closeReason?.name,
        grossPnl = trade.grossPnl?.value,
        fees = trade.fees?.value,
        slippageCost = trade.slippageCost?.value,
        netPnl = trade.netPnl?.value,
        returnPct = trade.returnPct,
        result = trade.result?.name,
        status = trade.status.name,
        openSignalId = trade.openSignalId.value,
        closeSignalId = trade.closeSignalId?.value,
    )

    fun toSignal(signal: TradingSignal): SignalResponse = SignalResponse(
        signalId = signal.signalId.value,
        symbol = signal.symbol.value,
        timeframe = signal.timeframe.value,
        side = signal.side.name,
        price = signal.price.value,
        candleOpenTime = signal.candleOpenTime,
        strategy = signal.strategy,
        confidence = signal.confidence,
        reason = signal.reason,
        stopLoss = signal.stopLoss?.value,
        takeProfit = signal.takeProfit?.value,
        status = signal.status.name,
        ignoreReason = signal.ignoreReason,
    )

    fun toStatistics(s: TradingStatistics): StatisticsResponse = StatisticsResponse(
        closedTrades = s.closedTrades,
        openTrades = s.openTrades,
        accumulatedGrossPnl = s.accumulatedGrossPnl,
        accumulatedFees = s.accumulatedFees,
        accumulatedSlippage = s.accumulatedSlippage,
        accumulatedNetPnl = s.accumulatedNetPnl,
        winRatePct = s.winRatePct,
        averageGain = s.averageGain,
        averageLoss = s.averageLoss,
        maxDrawdown = s.maxDrawdown,
        bySymbol = s.bySymbol.mapValues { StatBucketResponse(it.value.closedTrades, it.value.netPnl, it.value.winRatePct) },
        byStrategy = s.byStrategy.mapValues { StatBucketResponse(it.value.closedTrades, it.value.netPnl, it.value.winRatePct) },
        byTimeframe = s.byTimeframe.mapValues { StatBucketResponse(it.value.closedTrades, it.value.netPnl, it.value.winRatePct) },
    )
}
