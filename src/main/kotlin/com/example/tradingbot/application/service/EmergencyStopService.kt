package com.example.tradingbot.application.service

import com.example.tradingbot.application.port.input.ClosePaperTradeUseCase
import com.example.tradingbot.application.port.input.GetTradingStatisticsUseCase
import com.example.tradingbot.application.port.input.StatisticsFilters
import com.example.tradingbot.application.port.output.NotificationMessage
import com.example.tradingbot.application.port.output.NotificationPort
import com.example.tradingbot.application.port.output.StatisticsRepositoryPort
import com.example.tradingbot.application.port.output.TradingStatePort
import com.example.tradingbot.application.port.output.TransactionPort
import com.example.tradingbot.domain.model.CloseReason
import com.example.tradingbot.domain.model.PaperTrade
import com.example.tradingbot.domain.model.TradingStatistics
import org.slf4j.LoggerFactory

class GetTradingStatisticsService(
    private val statisticsRepository: StatisticsRepositoryPort,
) : GetTradingStatisticsUseCase {
    override fun getStatistics(filters: StatisticsFilters): TradingStatistics =
        statisticsRepository.statistics(filters)
}

class EmergencyStopService(
    private val transactionPort: TransactionPort,
    private val tradingStatePort: TradingStatePort,
    private val closePaperTrade: ClosePaperTradeUseCase,
    private val notificationPort: NotificationPort,
) {

    private val logger = LoggerFactory.getLogger(EmergencyStopService::class.java)

    fun stop(): String? =
        transactionPort.executeInTransaction {
            val state = tradingStatePort.lockState()
            tradingStatePort.setSignalsEnabled(false, "emergency-stop")
            tradingStatePort.setEmergencyActive()
            val closed: PaperTrade? = if (state.hasOpenTrade()) {
                try {
                    closePaperTrade.closeCurrent(CloseReason.EMERGENCY, actor = "emergency-stop")
                } catch (e: com.example.tradingbot.domain.exception.NoOpenTradeException) {
                    null
                }
            } else {
                null
            }
            logger.warn("EMERGENCY STOP activated; closed trade = {}", closed?.tradeId?.value)
            notificationPort.send(NotificationMessage.Emergency(closed?.tradeId?.value))
            closed?.tradeId?.value
        }
}
