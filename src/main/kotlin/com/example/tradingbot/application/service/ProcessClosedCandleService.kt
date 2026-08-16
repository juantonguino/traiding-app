package com.example.tradingbot.application.service

import com.example.tradingbot.application.port.input.ClosePaperTradeUseCase
import com.example.tradingbot.application.port.input.GenerateSignalUseCase
import com.example.tradingbot.application.port.input.OpenPaperTradeUseCase
import com.example.tradingbot.application.port.input.ProcessClosedCandleUseCase
import com.example.tradingbot.application.port.output.ObservabilityPort
import com.example.tradingbot.application.port.output.PaperTradeRepositoryPort
import com.example.tradingbot.application.port.output.SignalRepositoryPort
import com.example.tradingbot.application.port.output.TradingStatePort
import com.example.tradingbot.application.service.strategy.NotificationMessageService
import com.example.tradingbot.domain.exception.OpenTradeExistsException
import com.example.tradingbot.domain.model.IgnoreReasons
import com.example.tradingbot.domain.model.MarketCandle
import com.example.tradingbot.domain.model.SignalSide
import com.example.tradingbot.domain.model.SignalStatus
import com.example.tradingbot.domain.service.SignalEvaluator
import org.slf4j.LoggerFactory
import java.time.Instant

class ProcessClosedCandleService(
    private val generateSignal: GenerateSignalUseCase,
    private val openPaperTrade: OpenPaperTradeUseCase,
    private val closePaperTrade: ClosePaperTradeUseCase,
    private val signalRepository: SignalRepositoryPort,
    private val tradingStatePort: TradingStatePort,
    private val paperTradeRepository: PaperTradeRepositoryPort,
    private val notifications: NotificationMessageService,
    private val evaluator: SignalEvaluator,
    private val observability: ObservabilityPort,
) : ProcessClosedCandleUseCase {

    private val logger = LoggerFactory.getLogger(ProcessClosedCandleService::class.java)

    override fun process(candle: MarketCandle) {
        try {
            observability.candleProcessed()
            val signal = generateSignal.generate(candle) ?: return
            when (signal.side) {
                SignalSide.BUY -> openTrade(signal)
                SignalSide.SELL -> closeTrade(signal)
                SignalSide.HOLD -> signalRepository.update(signal.asAccepted())
            }
        } finally {
            tradingStatePort.recordCandleProcessed(Instant.now())
        }
    }

    private fun openTrade(signal: com.example.tradingbot.domain.model.TradingSignal) {
        try {
            openPaperTrade.open(signal)
        } catch (e: OpenTradeExistsException) {
            val ignored = signalRepository.update(
                signal.asIgnored(IgnoreReasons.GLOBAL_TRADE_ALREADY_OPEN)
            )
            observability.signalIgnored(IgnoreReasons.GLOBAL_TRADE_ALREADY_OPEN)
            logger.info("Signal ignored: signalId={} reason={} openSymbol={}",
                ignored.signalId.value, ignored.ignoreReason, e.openSymbol)
            notifications.signalIgnored(ignored, e.openSymbol)
        }
    }

    private fun closeTrade(signal: com.example.tradingbot.domain.model.TradingSignal) {
        val closed = closePaperTrade.closeBySellSignal(signal)
        if (closed == null) {
            val ignored = signalRepository.update(
                signal.asIgnored(IgnoreReasons.NO_MATCHING_OPEN_TRADE)
            )
            observability.signalIgnored(IgnoreReasons.NO_MATCHING_OPEN_TRADE)
            logger.info("Signal ignored: signalId={} reason={} (no matching open trade for {})",
                ignored.signalId.value, ignored.ignoreReason, signal.symbol.value)
            notifications.signalIgnored(ignored, null)
        }
    }
}
