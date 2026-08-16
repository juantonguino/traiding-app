package com.example.tradingbot.adapter.output.persistence

import com.example.tradingbot.adapter.output.persistence.mapper.TradingStateEntityMapper
import com.example.tradingbot.adapter.output.persistence.repository.TradingStateJpaRepository
import com.example.tradingbot.application.port.output.TradingStatePort
import com.example.tradingbot.domain.model.TradingState
import com.example.tradingbot.domain.valueobject.Symbol
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class TradingStatePersistenceAdapter(
    private val jpa: TradingStateJpaRepository,
    private val mapper: TradingStateEntityMapper,
) : TradingStatePort {

    override fun read(): TradingState =
        jpa.findSingle()?.let(mapper::toDomain)
            ?: error("trading_state row missing — run Flyway migrations (V4 seeds the single row)")

    override fun lockState(): TradingState =
        jpa.findLocked()?.let(mapper::toDomain)
            ?: error("trading_state row missing — run Flyway migrations (V4 seeds the single row)")

    override fun markOpen(tradeId: Long, symbol: Symbol) = update {
        it.openTradeId = tradeId
        it.openSymbol = symbol.value
    }

    override fun markClosed() = update {
        it.openTradeId = null
        it.openSymbol = null
    }

    override fun setSignalsEnabled(enabled: Boolean, actor: String?) = update {
        it.signalsEnabled = enabled
        if (enabled) {
            it.signalsDisabledBy = null
            it.signalsDisabledAt = null
        } else {
            it.signalsDisabledBy = actor
            it.signalsDisabledAt = Instant.now()
        }
    }

    override fun setEmergencyActive() = update {
        it.emergencyActive = true
        it.signalsEnabled = false
    }

    override fun clearEmergency() = update {
        it.emergencyActive = false
    }

    override fun recordCandleProcessed(now: Instant) = update {
        it.lastCandleProcessedAt = now
        it.marketDataHealthy = true
    }

    override fun setMarketDataHealthy(healthy: Boolean) = update {
        it.marketDataHealthy = healthy
    }

    private fun update(action: (com.example.tradingbot.adapter.output.persistence.entity.TradingStateEntity) -> Unit) {
        val entity = jpa.findSingle()
            ?: error("trading_state row missing — run Flyway migrations (V4 seeds the single row)")
        action(entity)
        entity.updatedAt = Instant.now()
        jpa.save(entity)
    }
}
