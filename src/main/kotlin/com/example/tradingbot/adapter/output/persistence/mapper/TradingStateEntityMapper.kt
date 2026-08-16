package com.example.tradingbot.adapter.output.persistence.mapper

import com.example.tradingbot.adapter.output.persistence.entity.TradingStateEntity
import com.example.tradingbot.domain.model.TradingState
import com.example.tradingbot.domain.valueobject.Symbol
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class TradingStateEntityMapper {

    fun toDomain(entity: TradingStateEntity): TradingState = TradingState(
        mode = entity.mode!!,
        signalsEnabled = entity.signalsEnabled!!,
        openTradeId = entity.openTradeId,
        openSymbol = entity.openSymbol?.let { Symbol(it) },
        emergencyActive = entity.emergencyActive!!,
        marketDataHealthy = entity.marketDataHealthy!!,
        lastCandleProcessedAt = entity.lastCandleProcessedAt,
        signalsDisabledBy = entity.signalsDisabledBy,
        signalsDisabledAt = entity.signalsDisabledAt,
    )

    fun apply(entity: TradingStateEntity, state: TradingState) {
        entity.mode = state.mode
        entity.signalsEnabled = state.signalsEnabled
        entity.openTradeId = state.openTradeId
        entity.openSymbol = state.openSymbol?.value
        entity.emergencyActive = state.emergencyActive
        entity.marketDataHealthy = state.marketDataHealthy
        entity.lastCandleProcessedAt = state.lastCandleProcessedAt
        entity.signalsDisabledBy = state.signalsDisabledBy
        entity.signalsDisabledAt = state.signalsDisabledAt
        entity.updatedAt = Instant.now()
    }
}
