package com.example.tradingbot.support

import com.example.tradingbot.application.port.input.SignalFilters
import com.example.tradingbot.application.port.input.TradeFilters
import com.example.tradingbot.application.port.output.ClockPort
import com.example.tradingbot.application.port.output.NotificationMessage
import com.example.tradingbot.application.port.output.NotificationPort
import com.example.tradingbot.application.port.output.ObservabilityPort
import com.example.tradingbot.application.port.output.PaperTradeRepositoryPort
import com.example.tradingbot.application.port.output.SignalRepositoryPort
import com.example.tradingbot.application.port.output.TradingStatePort
import com.example.tradingbot.application.port.output.TransactionPort
import com.example.tradingbot.domain.exception.NotFoundException
import com.example.tradingbot.domain.model.PaperTrade
import com.example.tradingbot.domain.model.TradingSignal
import com.example.tradingbot.domain.model.TradingState
import com.example.tradingbot.domain.valueobject.SignalId
import com.example.tradingbot.domain.valueobject.Symbol
import com.example.tradingbot.domain.valueobject.Timeframe
import com.example.tradingbot.domain.valueobject.TradeId
import java.math.BigDecimal
import java.time.Instant

class InMemorySignalRepository : SignalRepositoryPort {
    val signals = mutableListOf<TradingSignal>()

    override fun save(signal: TradingSignal): TradingSignal {
        signals.removeAll { it.signalId == signal.signalId }
        signals.add(signal)
        return signal
    }

    override fun update(signal: TradingSignal): TradingSignal = save(signal)

    override fun findById(signalId: SignalId): TradingSignal? =
        signals.firstOrNull { it.signalId == signalId }

    override fun findByCandleKey(symbol: Symbol, timeframe: Timeframe, candleOpenTime: Instant): TradingSignal? =
        signals.firstOrNull { it.symbol == symbol && it.timeframe == timeframe && it.candleOpenTime == candleOpenTime }

    override fun markUsedToOpen(signal: TradingSignal, tradeId: TradeId): TradingSignal =
        update(signal.asUsedToOpen())

    override fun markUsedToClose(signal: TradingSignal, tradeId: TradeId): TradingSignal =
        update(signal.asUsedToClose())

    override fun search(filters: SignalFilters): List<TradingSignal> =
        signals.filter { s ->
            (filters.symbol == null || s.symbol == filters.symbol) &&
                (filters.status == null || s.status.name.equals(filters.status, ignoreCase = true)) &&
                (filters.side == null || s.side.name.equals(filters.side, ignoreCase = true)) &&
                (filters.from == null || !s.candleOpenTime.isBefore(filters.from)) &&
                (filters.to == null || !s.candleOpenTime.isAfter(filters.to))
        }
}

class InMemoryPaperTradeRepository : PaperTradeRepositoryPort {
    val trades = mutableListOf<PaperTrade>()

    override fun save(trade: PaperTrade): PaperTrade {
        trades.removeAll { it.tradeId == trade.tradeId }
        trades.add(trade)
        return trade.copy(dbId = trade.dbId ?: trades.size.toLong())
    }

    override fun findOpenTrade(): PaperTrade? = trades.firstOrNull { it.status.name == "OPEN" }

    override fun findOpenBySymbol(symbol: Symbol): PaperTrade? =
        trades.firstOrNull { it.status.name == "OPEN" && it.symbol == symbol }

    override fun findByCorrelationId(tradeId: TradeId): PaperTrade? =
        trades.firstOrNull { it.tradeId == tradeId }

    override fun search(filters: TradeFilters): List<PaperTrade> =
        trades.filter { t ->
            (filters.symbol == null || t.symbol == filters.symbol) &&
                (filters.strategy == null || t.strategy == filters.strategy) &&
                (filters.timeframe == null || t.timeframe.value == filters.timeframe) &&
                (filters.from == null || !t.openTime.isBefore(filters.from)) &&
                (filters.to == null || t.closeTime == null || !t.closeTime!!.isAfter(filters.to))
        }

    override fun countOpen(): Long = trades.count { it.status.name == "OPEN" }.toLong()
}

class InMemoryTradingState(
    initial: TradingState = TradingState(
        mode = "PAPER",
        signalsEnabled = true,
        openTradeId = null,
        openSymbol = null,
        emergencyActive = false,
        marketDataHealthy = false,
        lastCandleProcessedAt = null,
        signalsDisabledBy = null,
        signalsDisabledAt = null,
    ),
) : TradingStatePort {
    var state: TradingState = initial

    override fun read(): TradingState = state

    override fun lockState(): TradingState = state

    override fun markOpen(tradeId: Long, symbol: Symbol) {
        state = state.copy(openTradeId = tradeId, openSymbol = symbol)
    }

    override fun markClosed() {
        state = state.copy(openTradeId = null, openSymbol = null)
    }

    override fun setSignalsEnabled(enabled: Boolean, actor: String?) {
        state = state.copy(
            signalsEnabled = enabled,
            signalsDisabledBy = if (enabled) null else actor,
            signalsDisabledAt = if (enabled) null else Instant.now(),
        )
    }

    override fun setEmergencyActive() {
        state = state.copy(emergencyActive = true, signalsEnabled = false)
    }

    override fun clearEmergency() {
        state = state.copy(emergencyActive = false)
    }

    override fun recordCandleProcessed(now: Instant) {
        state = state.copy(lastCandleProcessedAt = now, marketDataHealthy = true)
    }

    override fun setMarketDataHealthy(healthy: Boolean) {
        state = state.copy(marketDataHealthy = healthy)
    }
}

class DirectTransactionPort : TransactionPort {
    override fun <T> executeInTransaction(block: () -> T): T = block()
}

class FixedClockPort(private val now: Instant) : ClockPort {
    override fun now(): Instant = now
}

class RecordingNotificationPort : NotificationPort {
    val sent = mutableListOf<NotificationMessage>()

    override fun send(message: NotificationMessage) {
        sent.add(message)
    }
}

class NoOpObservabilityPort : ObservabilityPort {
    override fun candleProcessed() = Unit

    override fun signalGenerated() = Unit

    override fun signalIgnored(reason: String) = Unit

    override fun tradeOpened() = Unit

    override fun tradeClosed() = Unit

    override fun recordNetPnl(netPnl: BigDecimal) = Unit

    override fun adapterError(adapter: String, operation: String) = Unit

    override fun marketDataHealthy(healthy: Boolean) = Unit
}

fun findSignal(signals: List<TradingSignal>, signalId: String): TradingSignal =
    signals.firstOrNull { it.signalId.value == signalId } ?: throw NotFoundException("Signal", signalId)
