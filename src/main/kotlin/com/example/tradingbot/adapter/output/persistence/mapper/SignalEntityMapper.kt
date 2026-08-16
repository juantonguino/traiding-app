package com.example.tradingbot.adapter.output.persistence.mapper

import com.example.tradingbot.adapter.output.persistence.entity.SignalEntity
import com.example.tradingbot.domain.model.SignalStatus
import com.example.tradingbot.domain.model.TradingSignal
import com.example.tradingbot.domain.valueobject.Price
import com.example.tradingbot.domain.valueobject.SignalId
import com.example.tradingbot.domain.valueobject.Symbol
import com.example.tradingbot.domain.valueobject.Timeframe
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class SignalEntityMapper {

    fun toEntity(signal: TradingSignal): SignalEntity {
        val entity = SignalEntity()
        entity.signalId = signal.signalId.value
        entity.symbol = signal.symbol.value
        entity.timeframe = signal.timeframe.value
        entity.side = signal.side.name
        entity.price = signal.price.value
        entity.candleOpenTime = signal.candleOpenTime
        entity.candleCloseTime = signal.candleCloseTime
        entity.strategy = signal.strategy
        entity.confidence = signal.confidence
        entity.reason = signal.reason
        entity.stopLoss = signal.stopLoss?.value
        entity.takeProfit = signal.takeProfit?.value
        entity.status = signal.status.name
        entity.ignoreReason = signal.ignoreReason
        entity.createdAt = signal.createdAt
        entity.updatedAt = signal.createdAt
        return entity
    }

    fun toDomain(entity: SignalEntity): TradingSignal = TradingSignal(
        signalId = SignalId(entity.signalId!!),
        symbol = Symbol(entity.symbol!!),
        timeframe = Timeframe(entity.timeframe!!),
        side = com.example.tradingbot.domain.model.SignalSide.valueOf(entity.side!!),
        price = Price(entity.price!!),
        candleOpenTime = entity.candleOpenTime!!,
        candleCloseTime = entity.candleCloseTime!!,
        strategy = entity.strategy!!,
        confidence = entity.confidence,
        reason = entity.reason!!,
        stopLoss = entity.stopLoss?.let { Price(it) },
        takeProfit = entity.takeProfit?.let { Price(it) },
        status = SignalStatus.valueOf(entity.status!!),
        ignoreReason = entity.ignoreReason,
        createdAt = entity.createdAt ?: Instant.now(),
    )
}
