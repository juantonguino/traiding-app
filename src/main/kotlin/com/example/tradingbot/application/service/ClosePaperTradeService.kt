package com.example.tradingbot.application.service

import com.example.tradingbot.application.port.input.ClosePaperTradeUseCase
import com.example.tradingbot.application.port.output.ClockPort
import com.example.tradingbot.application.port.output.MarketDataPort
import com.example.tradingbot.application.port.output.ObservabilityPort
import com.example.tradingbot.application.port.output.PaperTradeRepositoryPort
import com.example.tradingbot.application.port.output.SignalRepositoryPort
import com.example.tradingbot.application.port.output.TradingStatePort
import com.example.tradingbot.application.port.output.TransactionPort
import com.example.tradingbot.application.service.strategy.NotificationMessageService
import com.example.tradingbot.domain.event.TradeClosedEvent
import com.example.tradingbot.domain.exception.NoOpenTradeException
import com.example.tradingbot.domain.model.CloseReason
import com.example.tradingbot.domain.model.PaperTrade
import com.example.tradingbot.domain.model.TradingSignal
import com.example.tradingbot.domain.service.TradeResultCalculator
import com.example.tradingbot.domain.valueobject.Price
import com.example.tradingbot.domain.valueobject.SignalId
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class ClosePaperTradeService(
    private val transactionPort: TransactionPort,
    private val tradingStatePort: TradingStatePort,
    private val paperTradeRepository: PaperTradeRepositoryPort,
    private val signalRepository: SignalRepositoryPort,
    private val marketDataPort: MarketDataPort,
    private val calculator: TradeResultCalculator,
    private val clockPort: ClockPort,
    private val notifications: NotificationMessageService,
    private val observability: ObservabilityPort,
) : ClosePaperTradeUseCase {

    private val logger = LoggerFactory.getLogger(ClosePaperTradeService::class.java)

    override fun closeCurrent(reason: CloseReason, actor: String?, closeSignalId: SignalId?): PaperTrade =
        transactionPort.executeInTransaction {
            val state = tradingStatePort.lockState()
            if (!state.hasOpenTrade()) {
                throw NoOpenTradeException()
            }
            val open = paperTradeRepository.findOpenTrade()
                ?: throw NoOpenTradeException()
            val exitPrice = marketDataPort.currentPrice(open.symbol)
            closeLocked(open, reason, exitPrice, actor, closeSignalId)
        }

    override fun closeCurrent(reason: CloseReason, exitPrice: Price, actor: String?, closeSignalId: SignalId?): PaperTrade =
        transactionPort.executeInTransaction {
            val state = tradingStatePort.lockState()
            if (!state.hasOpenTrade()) {
                throw NoOpenTradeException()
            }
            val open = paperTradeRepository.findOpenTrade()
                ?: throw NoOpenTradeException()
            closeLocked(open, reason, exitPrice, actor, closeSignalId)
        }

    override fun closeBySellSignal(signal: TradingSignal): PaperTrade? =
        transactionPort.executeInTransaction {
            val state = tradingStatePort.lockState()
            if (!state.hasOpenTrade()) {
                return@executeInTransaction null
            }
            val open = paperTradeRepository.findOpenBySymbol(signal.symbol)
            if (open == null) {
                return@executeInTransaction null
            }
            val closed = closeLocked(open, CloseReason.SELL_SIGNAL, signal.price, null, signal.signalId)
            signalRepository.markUsedToClose(signal, open.tradeId)
            closed
        }

    private fun closeLocked(
        open: PaperTrade,
        reason: CloseReason,
        exitPrice: Price,
        actor: String?,
        closeSignalId: SignalId?,
    ): PaperTrade {
        val now = clockPort.now()
        val computation = calculator.compute(open.entryPrice, exitPrice, open.quantity)
        val closed = open.closedWith(
            exitPrice = exitPrice,
            closeTime = now,
            closeReason = reason,
            computation = computation,
            closeSignalId = closeSignalId,
        )
        val saved = paperTradeRepository.save(closed)
        observability.tradeClosed()
        observability.recordNetPnl(saved.netPnl?.value ?: java.math.BigDecimal.ZERO)
        tradingStatePort.markClosed()

        MDC.put("tradeId", saved.tradeId.value)
        try {
            logger.info("Trade closed: tradeId={} symbol={} reason={} exit={} gross={} fees={} slippage={} net={} return={} result={} actor={}",
                saved.tradeId.value, saved.symbol.value, saved.closeReason, saved.exitPrice?.value,
                saved.grossPnl?.value, saved.fees?.value, saved.slippageCost?.value, saved.netPnl?.value,
                saved.returnPct, saved.result, actor)
            emit(TradeClosedEvent(
                signal = TradingSignal(
                    signalId = closeSignalId ?: open.openSignalId,
                    symbol = open.symbol,
                    timeframe = open.timeframe,
                    side = com.example.tradingbot.domain.model.SignalSide.SELL,
                    price = exitPrice,
                    candleOpenTime = open.openTime,
                    candleCloseTime = now,
                    strategy = open.strategy,
                    confidence = null,
                    reason = "closed by $reason",
                    stopLoss = null,
                    takeProfit = null,
                    status = com.example.tradingbot.domain.model.SignalStatus.USED_TO_CLOSE,
                    ignoreReason = null,
                    createdAt = now,
                ),
                tradeId = saved.tradeId,
                closeReason = reason,
                netPnl = saved.netPnl?.value ?: java.math.BigDecimal.ZERO,
                occurredAt = now,
            ))
        } finally {
            MDC.remove("tradeId")
        }
        notifications.tradeClosed(saved)
        return saved
    }

    private fun emit(event: TradeClosedEvent) {
        logger.info("Event emitted: {} correlationId={}", event::class.simpleName, event.correlationId)
    }
}
