package com.example.tradingbot.adapter.output.persistence

import com.example.tradingbot.adapter.output.persistence.mapper.PaperTradeEntityMapper
import com.example.tradingbot.adapter.output.persistence.repository.PaperTradeJpaRepository
import com.example.tradingbot.adapter.output.persistence.repository.SignalJpaRepository
import com.example.tradingbot.application.port.input.TradeFilters
import com.example.tradingbot.application.port.output.PaperTradeRepositoryPort
import com.example.tradingbot.domain.exception.NotFoundException
import com.example.tradingbot.domain.model.PaperTrade
import com.example.tradingbot.domain.model.TradeStatus
import com.example.tradingbot.domain.valueobject.Symbol
import com.example.tradingbot.domain.valueobject.TradeId
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class PaperTradePersistenceAdapter(
    private val jpa: PaperTradeJpaRepository,
    private val signalJpa: SignalJpaRepository,
    private val mapper: PaperTradeEntityMapper,
) : PaperTradeRepositoryPort {

    override fun save(trade: PaperTrade): PaperTrade {
        val openSignal = signalJpa.findBySignalId(trade.openSignalId.value)
            ?: throw NotFoundException("Signal", trade.openSignalId.value)
        val closeSignal = trade.closeSignalId?.let {
            signalJpa.findBySignalId(it.value) ?: throw NotFoundException("Signal", it.value)
        }
        val entity = mapper.toEntity(trade, openSignal, closeSignal)
        return mapper.toDomain(jpa.save(entity))
    }

    override fun findOpenTrade(): PaperTrade? =
        jpa.findByStatus(TradeStatus.OPEN.name).firstOrNull()?.let(mapper::toDomain)

    override fun findOpenBySymbol(symbol: Symbol): PaperTrade? =
        jpa.findByStatusAndSymbol(TradeStatus.OPEN.name, symbol.value)?.let(mapper::toDomain)

    override fun findByCorrelationId(tradeId: TradeId): PaperTrade? =
        jpa.findByTradeId(tradeId.value)?.let(mapper::toDomain)

    @Transactional(readOnly = true)
    override fun search(filters: TradeFilters): List<PaperTrade> =
        jpa.findAll(tradeSpec(filters)).map(mapper::toDomain)

    override fun countOpen(): Long = jpa.countByStatus(TradeStatus.OPEN.name)

    private fun tradeSpec(filters: TradeFilters): Specification<com.example.tradingbot.adapter.output.persistence.entity.PaperTradeEntity> =
        Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            predicates.add(cb.equal(root.get<String>("status"), TradeStatus.CLOSED.name))
            filters.symbol?.let { predicates.add(cb.equal(root.get<String>("symbol"), it.value)) }
            filters.strategy?.let { predicates.add(cb.equal(root.get<String>("strategy"), it)) }
            filters.timeframe?.let { predicates.add(cb.equal(root.get<String>("timeframe"), it)) }
            filters.from?.let { predicates.add(cb.greaterThanOrEqualTo(root.get<Instant>("openTime"), it)) }
            filters.to?.let { predicates.add(cb.lessThanOrEqualTo(root.get<Instant>("closeTime"), it)) }
            cb.and(*predicates.toTypedArray())
        }
}
