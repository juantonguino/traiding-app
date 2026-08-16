package com.example.tradingbot.adapter.output.persistence

import com.example.tradingbot.adapter.output.persistence.repository.PaperTradeJpaRepository
import com.example.tradingbot.application.port.input.StatisticsFilters
import com.example.tradingbot.application.port.output.StatisticsRepositoryPort
import com.example.tradingbot.domain.model.StatBucket
import com.example.tradingbot.domain.model.TradeStatus
import com.example.tradingbot.domain.model.TradingStatistics
import com.example.tradingbot.domain.valueobject.MoneyScale
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

@Component
class StatisticsPersistenceAdapter(
    private val jpa: PaperTradeJpaRepository,
) : StatisticsRepositoryPort {

    override fun statistics(filters: StatisticsFilters): TradingStatistics {
        val closed = jpa.findAll(closedSpec(filters))
        val openCount = jpa.countByStatus(TradeStatus.OPEN.name)

        if (closed.isEmpty()) {
            val empty = BigDecimal.ZERO.setScale(MoneyScale.SCALE, MoneyScale.ROUNDING)
            return TradingStatistics(
                closedTrades = 0,
                openTrades = openCount,
                accumulatedGrossPnl = empty,
                accumulatedFees = empty,
                accumulatedSlippage = empty,
                accumulatedNetPnl = empty,
                winRatePct = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                averageGain = empty,
                averageLoss = empty,
                maxDrawdown = empty,
                bySymbol = emptyMap(),
                byStrategy = emptyMap(),
                byTimeframe = emptyMap(),
            )
        }

        val netPnlList = closed.map { it.netPnl ?: BigDecimal.ZERO }
        val grossSum = closed.sumOf { it.grossPnl ?: BigDecimal.ZERO }
        val feesSum = closed.sumOf { it.fees ?: BigDecimal.ZERO }
        val slippageSum = closed.sumOf { it.slippageCost ?: BigDecimal.ZERO }
        val netSum = netPnlList.sumOf { it }
        val wins = netPnlList.count { it.signum() > 0 }
        val losses = netPnlList.count { it.signum() < 0 }
        val winRate = if (closed.isEmpty()) BigDecimal.ZERO else {
            BigDecimal(wins).divide(BigDecimal(closed.size), 4, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
        }
        val avgGain = if (wins == 0) BigDecimal.ZERO else {
            closed.filter { (it.netPnl ?: BigDecimal.ZERO).signum() > 0 }
                .sumOf { it.netPnl ?: BigDecimal.ZERO }
                .divide(BigDecimal(wins), MoneyScale.SCALE, MoneyScale.ROUNDING)
        }
        val avgLoss = if (losses == 0) BigDecimal.ZERO else {
            closed.filter { (it.netPnl ?: BigDecimal.ZERO).signum() < 0 }
                .sumOf { it.netPnl ?: BigDecimal.ZERO }
                .divide(BigDecimal(losses), MoneyScale.SCALE, MoneyScale.ROUNDING)
        }

        val ordered = closed.sortedBy { it.closeTime }
        var peak = BigDecimal.ZERO
        var cumulative = BigDecimal.ZERO
        var maxDrawdown = BigDecimal.ZERO
        ordered.forEach { trade ->
            cumulative = cumulative.add(trade.netPnl ?: BigDecimal.ZERO)
            if (cumulative > peak) peak = cumulative
            val dd = cumulative.subtract(peak)
            if (dd < maxDrawdown) maxDrawdown = dd
        }

        fun bucketOf(list: List<com.example.tradingbot.adapter.output.persistence.entity.PaperTradeEntity>): StatBucket {
            val n = list.size
            val net = list.sumOf { it.netPnl ?: BigDecimal.ZERO }
            val w = list.count { (it.netPnl ?: BigDecimal.ZERO).signum() > 0 }
            val rate = if (n == 0) BigDecimal.ZERO else
                BigDecimal(w).divide(BigDecimal(n), 4, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
            return StatBucket(closedTrades = n.toLong(), netPnl = net, winRatePct = rate)
        }

        val bySymbol = closed.groupBy { it.symbol!! }.mapValues { bucketOf(it.value) }
        val byStrategy = closed.groupBy { it.strategy!! }.mapValues { bucketOf(it.value) }
        val byTimeframe = closed.groupBy { it.timeframe!! }.mapValues { bucketOf(it.value) }

        return TradingStatistics(
            closedTrades = closed.size.toLong(),
            openTrades = openCount,
            accumulatedGrossPnl = grossSum.setScale(MoneyScale.SCALE, MoneyScale.ROUNDING),
            accumulatedFees = feesSum.setScale(MoneyScale.SCALE, MoneyScale.ROUNDING),
            accumulatedSlippage = slippageSum.setScale(MoneyScale.SCALE, MoneyScale.ROUNDING),
            accumulatedNetPnl = netSum.setScale(MoneyScale.SCALE, MoneyScale.ROUNDING),
            winRatePct = winRate.setScale(2, RoundingMode.HALF_UP),
            averageGain = avgGain,
            averageLoss = avgLoss,
            maxDrawdown = maxDrawdown.setScale(MoneyScale.SCALE, MoneyScale.ROUNDING),
            bySymbol = bySymbol,
            byStrategy = byStrategy,
            byTimeframe = byTimeframe,
        )
    }

    private fun closedSpec(filters: StatisticsFilters): Specification<com.example.tradingbot.adapter.output.persistence.entity.PaperTradeEntity> =
        Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            predicates.add(cb.equal(root.get<String>("status"), TradeStatus.CLOSED.name))
            filters.symbol?.let { predicates.add(cb.equal(root.get<String>("symbol"), it.value)) }
            filters.strategy?.let { predicates.add(cb.equal(root.get<String>("strategy"), it)) }
            filters.timeframe?.let { predicates.add(cb.equal(root.get<String>("timeframe"), it)) }
            filters.from?.let { predicates.add(cb.greaterThanOrEqualTo(root.get<Instant>("closeTime"), it)) }
            filters.to?.let { predicates.add(cb.lessThanOrEqualTo(root.get<Instant>("closeTime"), it)) }
            cb.and(*predicates.toTypedArray())
        }
}
