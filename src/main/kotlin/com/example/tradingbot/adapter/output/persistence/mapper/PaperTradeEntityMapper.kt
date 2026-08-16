package com.example.tradingbot.adapter.output.persistence.mapper

import com.example.tradingbot.adapter.output.persistence.entity.PaperTradeEntity
import com.example.tradingbot.adapter.output.persistence.entity.SignalEntity
import com.example.tradingbot.domain.model.CloseReason
import com.example.tradingbot.domain.model.PaperTrade
import com.example.tradingbot.domain.model.TradeResult
import com.example.tradingbot.domain.model.TradeStatus
import com.example.tradingbot.domain.valueobject.Money
import com.example.tradingbot.domain.valueobject.Price
import com.example.tradingbot.domain.valueobject.Quantity
import com.example.tradingbot.domain.valueobject.SignalId
import com.example.tradingbot.domain.valueobject.Symbol
import com.example.tradingbot.domain.valueobject.Timeframe
import com.example.tradingbot.domain.valueobject.TradeId
import org.springframework.stereotype.Component

@Component
class PaperTradeEntityMapper {

    fun toEntity(trade: PaperTrade, openSignal: SignalEntity, closeSignal: SignalEntity?): PaperTradeEntity {
        val entity = PaperTradeEntity()
        entity.id = trade.dbId
        entity.tradeId = trade.tradeId.value
        entity.symbol = trade.symbol.value
        entity.timeframe = trade.timeframe.value
        entity.strategy = trade.strategy
        entity.quantity = trade.quantity.value
        entity.entryPrice = trade.entryPrice.value
        entity.entryNotional = trade.entryNotional.value
        entity.openTime = trade.openTime
        entity.openSignal = openSignal
        entity.stopLoss = trade.stopLoss?.value
        entity.takeProfit = trade.takeProfit?.value
        entity.status = trade.status.name
        entity.closeReason = trade.closeReason?.name
        entity.exitPrice = trade.exitPrice?.value
        entity.closeTime = trade.closeTime
        entity.durationSeconds = trade.durationSeconds
        entity.closeSignal = closeSignal
        entity.grossPnl = trade.grossPnl?.value
        entity.fees = trade.fees?.value
        entity.slippageCost = trade.slippageCost?.value
        entity.netPnl = trade.netPnl?.value
        entity.returnPct = trade.returnPct
        entity.result = trade.result?.name
        entity.createdAt = trade.createdAt
        entity.updatedAt = trade.closeTime ?: trade.createdAt
        return entity
    }

    fun toDomain(entity: PaperTradeEntity): PaperTrade = PaperTrade(
        dbId = entity.id,
        tradeId = TradeId(entity.tradeId!!),
        symbol = Symbol(entity.symbol!!),
        timeframe = Timeframe(entity.timeframe!!),
        strategy = entity.strategy!!,
        quantity = Quantity(entity.quantity!!),
        entryPrice = Price(entity.entryPrice!!),
        entryNotional = Money(entity.entryNotional!!),
        openTime = entity.openTime!!,
        openSignalId = SignalId(entity.openSignal?.signalId!!),
        stopLoss = entity.stopLoss?.let { Price(it) },
        takeProfit = entity.takeProfit?.let { Price(it) },
        status = TradeStatus.valueOf(entity.status!!),
        closeReason = entity.closeReason?.let { CloseReason.valueOf(it) },
        exitPrice = entity.exitPrice?.let { Price(it) },
        closeTime = entity.closeTime,
        durationSeconds = entity.durationSeconds,
        closeSignalId = entity.closeSignal?.signalId?.let { SignalId(it) },
        grossPnl = entity.grossPnl?.let { Money(it) },
        fees = entity.fees?.let { Money(it) },
        slippageCost = entity.slippageCost?.let { Money(it) },
        netPnl = entity.netPnl?.let { Money(it) },
        returnPct = entity.returnPct,
        result = entity.result?.let { TradeResult.valueOf(it) },
        createdAt = entity.createdAt!!,
    )
}
