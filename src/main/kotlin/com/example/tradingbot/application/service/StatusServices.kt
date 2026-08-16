package com.example.tradingbot.application.service

import com.example.tradingbot.application.port.input.DisableSignalProcessingUseCase
import com.example.tradingbot.application.port.input.EnableSignalProcessingUseCase
import com.example.tradingbot.application.port.input.GetOpenTradeUseCase
import com.example.tradingbot.application.port.input.GetSignalsUseCase
import com.example.tradingbot.application.port.input.GetSystemStatusUseCase
import com.example.tradingbot.application.port.input.GetTradesUseCase
import com.example.tradingbot.application.port.input.SignalFilters
import com.example.tradingbot.application.port.input.SystemStatus
import com.example.tradingbot.application.port.input.TradeFilters
import com.example.tradingbot.application.port.output.ClockPort
import com.example.tradingbot.application.port.output.PaperTradeRepositoryPort
import com.example.tradingbot.application.port.output.SignalRepositoryPort
import com.example.tradingbot.application.port.output.TradingStatePort
import com.example.tradingbot.application.port.output.TransactionPort
import com.example.tradingbot.domain.exception.NotFoundException
import com.example.tradingbot.domain.model.PaperTrade
import com.example.tradingbot.domain.model.TradingSignal
import com.example.tradingbot.domain.model.TradingState
import com.example.tradingbot.domain.valueobject.TradeId
import org.slf4j.LoggerFactory

class GetOpenTradeService(
    private val paperTradeRepository: PaperTradeRepositoryPort,
) : GetOpenTradeUseCase {
    override fun getOpenTrade(): PaperTrade? = paperTradeRepository.findOpenTrade()
}

class GetTradesService(
    private val paperTradeRepository: PaperTradeRepositoryPort,
) : GetTradesUseCase {
    override fun search(filters: TradeFilters): List<PaperTrade> =
        paperTradeRepository.search(filters)

    override fun getByTradeId(tradeId: TradeId): PaperTrade? =
        paperTradeRepository.findByCorrelationId(tradeId)
}

class GetSignalsService(
    private val signalRepository: SignalRepositoryPort,
) : GetSignalsUseCase {
    override fun search(filters: SignalFilters): List<TradingSignal> =
        signalRepository.search(filters)
}

class GetSystemStatusService(
    private val tradingStatePort: TradingStatePort,
    private val getOpenTrade: GetOpenTradeUseCase,
) : GetSystemStatusUseCase {
    override fun status(): SystemStatus {
        val state = tradingStatePort.read()
        return SystemStatus(
            mode = state.mode,
            signalsEnabled = state.signalsEnabled,
            emergencyActive = state.emergencyActive,
            openTrade = getOpenTrade.getOpenTrade(),
            marketDataHealthy = state.marketDataHealthy,
            lastCandleProcessedAt = state.lastCandleProcessedAt,
            signalsDisabledBy = state.signalsDisabledBy,
            signalsDisabledAt = state.signalsDisabledAt,
        )
    }
}

class EnableSignalProcessingService(
    private val transactionPort: TransactionPort,
    private val tradingStatePort: TradingStatePort,
) : EnableSignalProcessingUseCase {

    private val logger = LoggerFactory.getLogger(EnableSignalProcessingService::class.java)

    override fun enable(actor: String?): TradingState =
        transactionPort.executeInTransaction {
            tradingStatePort.lockState()
            tradingStatePort.setSignalsEnabled(true, actor)
            tradingStatePort.clearEmergency()
            logger.info("Signal processing enabled by {}", actor ?: "unknown")
            tradingStatePort.read()
        }
}

class DisableSignalProcessingService(
    private val transactionPort: TransactionPort,
    private val tradingStatePort: TradingStatePort,
) : DisableSignalProcessingUseCase {

    private val logger = LoggerFactory.getLogger(DisableSignalProcessingService::class.java)

    override fun disable(actor: String): TradingState =
        transactionPort.executeInTransaction {
            tradingStatePort.lockState()
            tradingStatePort.setSignalsEnabled(false, actor)
            logger.info("Signal processing disabled by {}", actor)
            tradingStatePort.read()
        }
}
