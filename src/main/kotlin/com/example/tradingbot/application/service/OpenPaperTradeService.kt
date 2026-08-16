package com.example.tradingbot.application.service

import com.example.tradingbot.application.port.input.OpenPaperTradeUseCase
import com.example.tradingbot.application.port.output.ObservabilityPort
import com.example.tradingbot.application.port.output.PaperTradeRepositoryPort
import com.example.tradingbot.application.port.output.SignalRepositoryPort
import com.example.tradingbot.application.port.output.TradingStatePort
import com.example.tradingbot.application.port.output.TransactionPort
import com.example.tradingbot.application.port.output.ClockPort
import com.example.tradingbot.application.service.strategy.NotificationMessageService
import com.example.tradingbot.domain.event.TradeOpenedEvent
import com.example.tradingbot.domain.exception.OpenTradeExistsException
import com.example.tradingbot.domain.model.PaperTrade
import com.example.tradingbot.domain.model.TradingSignal
import com.example.tradingbot.domain.service.TradeResultCalculator
import com.example.tradingbot.domain.valueobject.Money
import com.example.tradingbot.domain.valueobject.Price
import com.example.tradingbot.domain.valueobject.TradeId
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.UUID

class OpenPaperTradeService(
    private val transactionPort: TransactionPort,
    private val tradingStatePort: TradingStatePort,
    private val paperTradeRepository: PaperTradeRepositoryPort,
    private val signalRepository: SignalRepositoryPort,
    private val calculator: TradeResultCalculator,
    private val clockPort: ClockPort,
    private val config: TradingConfig,
    private val notifications: NotificationMessageService,
    private val observability: ObservabilityPort,
) : OpenPaperTradeUseCase {

    private val logger = LoggerFactory.getLogger(OpenPaperTradeService::class.java)

    override fun open(signal: TradingSignal): PaperTrade =
        transactionPort.executeInTransaction {
            val state = tradingStatePort.lockState()
            if (state.emergencyActive) {
                throw com.example.tradingbot.domain.exception.EmergencyActiveException()
            }
            if (!state.signalsEnabled) {
                throw com.example.tradingbot.domain.exception.SignalsDisabledException()
            }
            if (state.hasOpenTrade()) {
                throw OpenTradeExistsException(state.openSymbol)
            }

            val now = clockPort.now()
            val quantity = calculator.quantityForNotional(config.entryNotionalUsdt, signal.price)
            val entryNotional = signal.price.value.multiply(quantity.value)
            val stopLoss = signal.stopLoss ?: config.stopLossPercent?.let {
                Price(signal.price.value.multiply(java.math.BigDecimal.ONE.subtract(it)))
            }
            val takeProfit = signal.takeProfit ?: config.takeProfitPercent?.let {
                Price(signal.price.value.multiply(java.math.BigDecimal.ONE.add(it)))
            }

            val trade = PaperTrade.open(
                tradeId = TradeId(UUID.randomUUID().toString()),
                symbol = signal.symbol,
                timeframe = signal.timeframe,
                strategy = signal.strategy,
                quantity = quantity,
                entryPrice = signal.price,
                entryNotional = Money(entryNotional),
                openTime = now,
                openSignalId = signal.signalId,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
            )

            val saved = paperTradeRepository.save(trade)
            observability.tradeOpened()
            tradingStatePort.markOpen(saved.dbId!!, saved.symbol)
            signalRepository.markUsedToOpen(signal, saved.tradeId)

            MDC.put("tradeId", saved.tradeId.value)
            try {
                logger.info("Trade opened: tradeId={} symbol={} qty={} entry={} signalId={}",
                    saved.tradeId.value, saved.symbol.value, saved.quantity.value, saved.entryPrice.value, signal.signalId.value)
                emit(TradeOpenedEvent(signal, saved.tradeId, now))
            } finally {
                MDC.remove("tradeId")
            }
            notifications.tradeOpened(saved)
            saved
        }

    private fun emit(event: TradeOpenedEvent) {
        logger.info("Event emitted: {} correlationId={}", event::class.simpleName, event.correlationId)
    }
}
