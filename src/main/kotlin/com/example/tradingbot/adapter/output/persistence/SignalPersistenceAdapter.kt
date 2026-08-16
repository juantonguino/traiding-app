package com.example.tradingbot.adapter.output.persistence

import com.example.tradingbot.adapter.output.persistence.mapper.SignalEntityMapper
import com.example.tradingbot.adapter.output.persistence.repository.PaperTradeJpaRepository
import com.example.tradingbot.adapter.output.persistence.repository.SignalJpaRepository
import com.example.tradingbot.application.port.input.SignalFilters
import com.example.tradingbot.application.port.output.SignalRepositoryPort
import com.example.tradingbot.domain.exception.NotFoundException
import com.example.tradingbot.domain.model.SignalStatus
import com.example.tradingbot.domain.model.TradingSignal
import com.example.tradingbot.domain.valueobject.SignalId
import com.example.tradingbot.domain.valueobject.Symbol
import com.example.tradingbot.domain.valueobject.Timeframe
import com.example.tradingbot.domain.valueobject.TradeId
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class SignalPersistenceAdapter(
    private val jpa: SignalJpaRepository,
    private val tradeJpa: PaperTradeJpaRepository,
    private val mapper: SignalEntityMapper,
) : SignalRepositoryPort {

    override fun save(signal: TradingSignal): TradingSignal {
        jpa.findBySymbolAndTimeframeAndCandleOpenTimeAndStrategy(
            signal.symbol.value, signal.timeframe.value, signal.candleOpenTime, signal.strategy,
        )?.let { return mapper.toDomain(it) }

        val entity = mapper.toEntity(signal)
        return mapper.toDomain(jpa.save(entity))
    }

    override fun update(signal: TradingSignal): TradingSignal {
        val entity = jpa.findBySignalId(signal.signalId.value)
            ?: throw NotFoundException("Signal", signal.signalId.value)
        entity.status = signal.status.name
        entity.ignoreReason = signal.ignoreReason
        entity.updatedAt = Instant.now()
        return mapper.toDomain(jpa.save(entity))
    }

    override fun findById(signalId: SignalId): TradingSignal? =
        jpa.findBySignalId(signalId.value)?.let(mapper::toDomain)

    override fun findByCandleKey(symbol: Symbol, timeframe: Timeframe, candleOpenTime: Instant): TradingSignal? =
        jpa.findBySymbolAndTimeframeAndCandleOpenTime(
            symbol.value, timeframe.value, candleOpenTime,
        )?.let(mapper::toDomain)

    override fun markUsedToOpen(signal: TradingSignal, tradeId: TradeId): TradingSignal {
        val entity = jpa.findBySignalId(signal.signalId.value)
            ?: throw NotFoundException("Signal", signal.signalId.value)
        val trade = tradeJpa.findByTradeId(tradeId.value)
            ?: throw NotFoundException("PaperTrade", tradeId.value)
        entity.openTrade = trade
        entity.status = SignalStatus.USED_TO_OPEN.name
        entity.updatedAt = Instant.now()
        return mapper.toDomain(jpa.save(entity))
    }

    override fun markUsedToClose(signal: TradingSignal, tradeId: TradeId): TradingSignal {
        val entity = jpa.findBySignalId(signal.signalId.value)
            ?: throw NotFoundException("Signal", signal.signalId.value)
        entity.status = SignalStatus.USED_TO_CLOSE.name
        entity.updatedAt = Instant.now()
        jpa.save(entity)

        val trade = tradeJpa.findByTradeId(tradeId.value)
            ?: throw NotFoundException("PaperTrade", tradeId.value)
        trade.closeSignal = entity
        trade.updatedAt = Instant.now()
        tradeJpa.save(trade)
        return mapper.toDomain(entity)
    }

    override fun search(filters: SignalFilters): List<TradingSignal> =
        jpa.findAll(signalSpec(filters)).map(mapper::toDomain)

    private fun signalSpec(filters: SignalFilters): Specification<com.example.tradingbot.adapter.output.persistence.entity.SignalEntity> =
        Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            filters.symbol?.let { predicates.add(cb.equal(root.get<String>("symbol"), it.value)) }
            filters.status?.let { predicates.add(cb.equal(root.get<String>("status"), it.uppercase())) }
            filters.side?.let { predicates.add(cb.equal(root.get<String>("side"), it.uppercase())) }
            filters.from?.let { predicates.add(cb.greaterThanOrEqualTo(root.get<Instant>("candleOpenTime"), it)) }
            filters.to?.let { predicates.add(cb.lessThanOrEqualTo(root.get<Instant>("candleOpenTime"), it)) }
            cb.and(*predicates.toTypedArray())
        }
}
