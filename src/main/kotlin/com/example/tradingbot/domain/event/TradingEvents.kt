package com.example.tradingbot.domain.event

import com.example.tradingbot.domain.model.TradingSignal
import java.time.Instant

sealed class TradingEvent(
    val correlationId: String,
    val occurredAt: Instant,
)

class SignalGeneratedEvent(signal: TradingSignal, occurredAt: Instant) :
    TradingEvent(signal.signalId.value, occurredAt)

class SignalIgnoredEvent(
    signal: TradingSignal,
    val openSymbol: com.example.tradingbot.domain.valueobject.Symbol?,
    occurredAt: Instant,
) : TradingEvent(signal.signalId.value, occurredAt)

class TradeOpenedEvent(
    signal: TradingSignal,
    val tradeId: com.example.tradingbot.domain.valueobject.TradeId,
    occurredAt: Instant,
) : TradingEvent(signal.signalId.value, occurredAt)

class TradeClosedEvent(
    signal: TradingSignal,
    val tradeId: com.example.tradingbot.domain.valueobject.TradeId,
    val closeReason: com.example.tradingbot.domain.model.CloseReason,
    val netPnl: java.math.BigDecimal,
    occurredAt: Instant,
) : TradingEvent(signal.signalId.value, occurredAt)
