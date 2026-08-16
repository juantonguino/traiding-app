package com.example.tradingbot.application.service

import com.example.tradingbot.application.port.input.GenerateSignalUseCase
import com.example.tradingbot.application.port.output.ObservabilityPort
import com.example.tradingbot.application.port.output.SignalRepositoryPort
import com.example.tradingbot.application.port.output.TradingStatePort
import com.example.tradingbot.application.service.strategy.CandleHistory
import com.example.tradingbot.application.service.strategy.NotificationMessageService
import com.example.tradingbot.application.service.strategy.TradingStrategy
import com.example.tradingbot.domain.event.SignalGeneratedEvent
import com.example.tradingbot.domain.model.MarketCandle
import com.example.tradingbot.domain.model.SignalStatus
import com.example.tradingbot.domain.model.TradingSignal
import com.example.tradingbot.domain.valueobject.SignalId
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.Instant
import java.util.UUID

class GenerateSignalService(
    private val strategy: TradingStrategy,
    private val candleHistory: CandleHistory,
    private val signalRepository: SignalRepositoryPort,
    private val tradingStatePort: TradingStatePort,
    private val notifications: NotificationMessageService,
    private val observability: ObservabilityPort,
) : GenerateSignalUseCase {

    private val logger = LoggerFactory.getLogger(GenerateSignalService::class.java)

    override fun generate(candle: MarketCandle): TradingSignal? {
        val state = tradingStatePort.read()
        if (!state.signalsEnabled || state.emergencyActive) {
            logger.info("Skipping candle {} {} {}: signals disabled or emergency active", candle.symbol, candle.timeframe, candle.openTime)
            return null
        }

        val existing = signalRepository.findByCandleKey(candle.symbol, candle.timeframe, candle.openTime)
        if (existing != null) {
            logger.info("Duplicate candle {} {} {} ignored; existing signal {}",
                candle.symbol, candle.timeframe, candle.openTime, existing.signalId.value)
            return existing
        }

        val decision = strategy.evaluate(candle, candleHistory)
        val signal = TradingSignal(
            signalId = SignalId(UUID.randomUUID().toString()),
            symbol = candle.symbol,
            timeframe = candle.timeframe,
            side = decision.side,
            price = decision.price,
            candleOpenTime = candle.openTime,
            candleCloseTime = candle.closeTime,
            strategy = strategy.id,
            confidence = decision.confidence,
            reason = decision.reason,
            stopLoss = decision.stopLoss,
            takeProfit = decision.takeProfit,
            status = SignalStatus.GENERATED,
            ignoreReason = null,
            createdAt = Instant.now(),
        )

        val persisted = signalRepository.save(signal)
        observability.signalGenerated()
        MDC.put("signalId", persisted.signalId.value)
        try {
            logger.info("Signal generated: {} {} {} @ {} status={} signalId={}",
                persisted.side, persisted.symbol, persisted.timeframe, persisted.price.value, persisted.status, persisted.signalId.value)
            emit(SignalGeneratedEvent(persisted, persisted.createdAt))
        } finally {
            MDC.remove("signalId")
        }

        notifications.signalGenerated(persisted)
        return persisted
    }

    private fun emit(event: SignalGeneratedEvent) {
        logger.info("Event emitted: {} correlationId={}", event::class.simpleName, event.correlationId)
    }
}
