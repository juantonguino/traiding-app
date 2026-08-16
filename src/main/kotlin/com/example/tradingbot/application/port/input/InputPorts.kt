package com.example.tradingbot.application.port.input

import com.example.tradingbot.domain.model.CloseReason
import com.example.tradingbot.domain.model.MarketCandle
import com.example.tradingbot.domain.model.PaperTrade
import com.example.tradingbot.domain.model.TradingSignal
import com.example.tradingbot.domain.model.TradingState
import com.example.tradingbot.domain.model.TradingStatistics
import com.example.tradingbot.domain.valueobject.Price
import com.example.tradingbot.domain.valueobject.SignalId
import com.example.tradingbot.domain.valueobject.Symbol
import com.example.tradingbot.domain.valueobject.TradeId
import java.time.Instant

interface ProcessClosedCandleUseCase {
    fun process(candle: MarketCandle)
}

interface GenerateSignalUseCase {
    fun generate(candle: MarketCandle): TradingSignal?
}

interface OpenPaperTradeUseCase {
    fun open(signal: TradingSignal): PaperTrade
}

interface ClosePaperTradeUseCase {
    fun closeCurrent(reason: CloseReason, actor: String? = null, closeSignalId: SignalId? = null): PaperTrade

    fun closeCurrent(reason: CloseReason, exitPrice: Price, actor: String? = null, closeSignalId: SignalId? = null): PaperTrade

    fun closeBySellSignal(signal: TradingSignal): PaperTrade?
}

interface EvaluateOpenTradeUseCase {
    fun evaluate(now: Instant = Instant.now())
}

interface GetOpenTradeUseCase {
    fun getOpenTrade(): PaperTrade?
}

interface GetTradingStatisticsUseCase {
    fun getStatistics(filters: StatisticsFilters = StatisticsFilters()): TradingStatistics
}

interface GetTradesUseCase {
    fun search(filters: TradeFilters = TradeFilters()): List<PaperTrade>

    fun getByTradeId(tradeId: TradeId): PaperTrade?
}

interface GetSignalsUseCase {
    fun search(filters: SignalFilters = SignalFilters()): List<TradingSignal>
}

data class StatisticsFilters(
    val symbol: Symbol? = null,
    val strategy: String? = null,
    val timeframe: String? = null,
    val from: Instant? = null,
    val to: Instant? = null,
)

data class SignalFilters(
    val symbol: Symbol? = null,
    val status: String? = null,
    val side: String? = null,
    val from: Instant? = null,
    val to: Instant? = null,
)

data class TradeFilters(
    val symbol: Symbol? = null,
    val strategy: String? = null,
    val timeframe: String? = null,
    val from: Instant? = null,
    val to: Instant? = null,
)

data class SystemStatus(
    val mode: String,
    val signalsEnabled: Boolean,
    val emergencyActive: Boolean,
    val openTrade: PaperTrade?,
    val marketDataHealthy: Boolean,
    val lastCandleProcessedAt: Instant?,
    val signalsDisabledBy: String?,
    val signalsDisabledAt: Instant?,
)

interface EnableSignalProcessingUseCase {
    fun enable(actor: String? = null): TradingState
}

interface DisableSignalProcessingUseCase {
    fun disable(actor: String): TradingState
}

interface GetSystemStatusUseCase {
    fun status(): SystemStatus
}

interface ManualCloseRequest {
    val reason: CloseReason
    val actor: String?
}
