package com.example.tradingbot.application.port.output

import com.example.tradingbot.application.port.input.SignalFilters
import com.example.tradingbot.application.port.input.StatisticsFilters
import com.example.tradingbot.application.port.input.TradeFilters
import com.example.tradingbot.domain.model.PaperTrade
import com.example.tradingbot.domain.model.TradingSignal
import com.example.tradingbot.domain.model.TradingState
import com.example.tradingbot.domain.model.TradingStatistics
import com.example.tradingbot.domain.valueobject.Price
import com.example.tradingbot.domain.valueobject.SignalId
import com.example.tradingbot.domain.valueobject.Symbol
import com.example.tradingbot.domain.valueobject.Timeframe
import com.example.tradingbot.domain.valueobject.TradeId
import java.time.Instant

interface MarketDataPort {
    fun currentPrice(symbol: Symbol): Price
}

interface SignalRepositoryPort {
    fun save(signal: TradingSignal): TradingSignal

    fun update(signal: TradingSignal): TradingSignal

    fun findById(signalId: SignalId): TradingSignal?

    fun findByCandleKey(symbol: Symbol, timeframe: Timeframe, candleOpenTime: Instant): TradingSignal?

    fun markUsedToOpen(signal: TradingSignal, tradeId: TradeId): TradingSignal

    fun markUsedToClose(signal: TradingSignal, tradeId: TradeId): TradingSignal

    fun search(filters: SignalFilters): List<TradingSignal>
}

interface PaperTradeRepositoryPort {
    fun save(trade: PaperTrade): PaperTrade

    fun findOpenTrade(): PaperTrade?

    fun findOpenBySymbol(symbol: Symbol): PaperTrade?

    fun findByCorrelationId(tradeId: TradeId): PaperTrade?

    fun search(filters: TradeFilters): List<PaperTrade>

    fun countOpen(): Long
}

interface TradingStatePort {
    fun read(): TradingState

    fun lockState(): TradingState

    fun markOpen(tradeId: Long, symbol: Symbol)

    fun markClosed()

    fun setSignalsEnabled(enabled: Boolean, actor: String?)

    fun setEmergencyActive()

    fun clearEmergency()

    fun recordCandleProcessed(now: Instant)

    fun setMarketDataHealthy(healthy: Boolean)
}

interface StatisticsRepositoryPort {
    fun statistics(filters: StatisticsFilters): TradingStatistics
}

interface ClockPort {
    fun now(): Instant
}

interface TransactionPort {
    fun <T> executeInTransaction(block: () -> T): T
}
