package com.example.tradingbot.adapter.output.persistence.repository

import com.example.tradingbot.adapter.output.persistence.entity.PaperTradeEntity
import com.example.tradingbot.adapter.output.persistence.entity.SignalEntity
import com.example.tradingbot.adapter.output.persistence.entity.TradingStateEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SignalJpaRepository : JpaRepository<SignalEntity, Long>, JpaSpecificationExecutor<SignalEntity> {

    fun findBySignalId(signalId: String): SignalEntity?

    fun findBySymbolAndTimeframeAndCandleOpenTime(
        symbol: String,
        timeframe: String,
        candleOpenTime: java.time.Instant,
    ): SignalEntity?

    fun findBySymbolAndTimeframeAndCandleOpenTimeAndStrategy(
        symbol: String,
        timeframe: String,
        candleOpenTime: java.time.Instant,
        strategy: String,
    ): SignalEntity?
}

interface PaperTradeJpaRepository : JpaRepository<PaperTradeEntity, Long>, JpaSpecificationExecutor<PaperTradeEntity> {

    @EntityGraph(attributePaths = ["openSignal", "closeSignal"])
    fun findByStatus(status: String): List<PaperTradeEntity>

    @EntityGraph(attributePaths = ["openSignal", "closeSignal"])
    fun findByTradeId(tradeId: String): PaperTradeEntity?

    @EntityGraph(attributePaths = ["openSignal", "closeSignal"])
    fun findByStatusAndSymbol(status: String, symbol: String): PaperTradeEntity?

    fun countByStatus(status: String): Long

    @Query(
        "select t from PaperTradeEntity t join fetch t.openSignal " +
            "where t.status = :status order by t.openTime desc",
    )
    fun findOpenWithSignal(@Param("status") status: String): List<PaperTradeEntity>
}

interface TradingStateJpaRepository : JpaRepository<TradingStateEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TradingStateEntity s where s.id = 1")
    fun findLocked(): TradingStateEntity?

    @Query("select s from TradingStateEntity s where s.id = 1")
    fun findSingle(): TradingStateEntity?
}
