package com.example.tradingbot.domain.model

import com.example.tradingbot.domain.valueobject.Money
import com.example.tradingbot.domain.valueobject.Price
import com.example.tradingbot.domain.valueobject.Quantity
import com.example.tradingbot.domain.valueobject.SignalId
import com.example.tradingbot.domain.valueobject.Symbol
import com.example.tradingbot.domain.valueobject.Timeframe
import com.example.tradingbot.domain.valueobject.TradeId
import java.math.BigDecimal
import java.time.Instant

data class TradeResultComputation(
    val entryNotional: Money,
    val exitNotional: Money,
    val grossPnl: Money,
    val fees: Money,
    val slippageCost: Money,
    val netPnl: Money,
    val returnPct: BigDecimal,
    val result: TradeResult,
)

data class PaperTrade(
    val dbId: Long? = null,
    val tradeId: TradeId,
    val symbol: Symbol,
    val timeframe: Timeframe,
    val strategy: String,
    val quantity: Quantity,
    val entryPrice: Price,
    val entryNotional: Money,
    val openTime: Instant,
    val openSignalId: SignalId,
    val stopLoss: Price?,
    val takeProfit: Price?,
    val status: TradeStatus,
    val closeReason: CloseReason?,
    val exitPrice: Price?,
    val closeTime: Instant?,
    val durationSeconds: Long?,
    val closeSignalId: SignalId?,
    val grossPnl: Money?,
    val fees: Money?,
    val slippageCost: Money?,
    val netPnl: Money?,
    val returnPct: BigDecimal?,
    val result: TradeResult?,
    val createdAt: Instant,
) {
    fun closedWith(
        exitPrice: Price,
        closeTime: Instant,
        closeReason: CloseReason,
        computation: TradeResultComputation,
        closeSignalId: SignalId? = null,
    ): PaperTrade = copy(
        status = TradeStatus.CLOSED,
        exitPrice = exitPrice,
        closeTime = closeTime,
        closeReason = closeReason,
        closeSignalId = closeSignalId,
        durationSeconds = java.time.Duration.between(openTime, closeTime).seconds,
        grossPnl = computation.grossPnl,
        fees = computation.fees,
        slippageCost = computation.slippageCost,
        netPnl = computation.netPnl,
        returnPct = computation.returnPct,
        result = computation.result,
    )

    companion object {
        fun open(
            tradeId: TradeId,
            symbol: Symbol,
            timeframe: Timeframe,
            strategy: String,
            quantity: Quantity,
            entryPrice: Price,
            entryNotional: Money,
            openTime: Instant,
            openSignalId: SignalId,
            stopLoss: Price?,
            takeProfit: Price?,
        ): PaperTrade = PaperTrade(
            tradeId = tradeId,
            symbol = symbol,
            timeframe = timeframe,
            strategy = strategy,
            quantity = quantity,
            entryPrice = entryPrice,
            entryNotional = entryNotional,
            openTime = openTime,
            openSignalId = openSignalId,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            status = TradeStatus.OPEN,
            closeReason = null,
            exitPrice = null,
            closeTime = null,
            durationSeconds = null,
            closeSignalId = null,
            grossPnl = null,
            fees = null,
            slippageCost = null,
            netPnl = null,
            returnPct = null,
            result = null,
            createdAt = openTime,
        )
    }
}
