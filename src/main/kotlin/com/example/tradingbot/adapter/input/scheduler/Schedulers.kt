package com.example.tradingbot.adapter.input.scheduler

import com.example.tradingbot.adapter.input.binance.KlineWebSocketListener
import com.example.tradingbot.application.port.input.EvaluateOpenTradeUseCase
import com.example.tradingbot.application.port.input.GetTradingStatisticsUseCase
import com.example.tradingbot.application.port.output.NotificationMessage
import com.example.tradingbot.application.port.output.NotificationPort
import com.example.tradingbot.application.port.output.ObservabilityPort
import com.example.tradingbot.application.port.output.TradingStatePort
import com.example.tradingbot.configuration.TradingProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["trading.scheduling.enabled"], havingValue = "true", matchIfMissing = true)
class TradeEvaluationScheduler(
    private val evaluateOpenTrade: EvaluateOpenTradeUseCase,
) {
    private val logger = LoggerFactory.getLogger(TradeEvaluationScheduler::class.java)

    @Scheduled(fixedDelayString = "\${trading.evaluation-interval-seconds:60}000", initialDelay = 60_000)
    fun evaluate() {
        try {
            evaluateOpenTrade.evaluate()
        } catch (e: Exception) {
            logger.error("Open trade evaluation failed: errorClass={} message={}", e::class.java.simpleName, e.message)
        }
    }
}

@Component
@ConditionalOnProperty(name = ["trading.scheduling.enabled"], havingValue = "true", matchIfMissing = true)
class DataAbsenceWatchdog(
    private val properties: TradingProperties,
    private val listener: KlineWebSocketListener,
    private val tradingStatePort: TradingStatePort,
    private val notificationPort: NotificationPort,
    private val observability: ObservabilityPort,
) {
    private val logger = LoggerFactory.getLogger(DataAbsenceWatchdog::class.java)

    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
    fun check() {
        val lastMessage = listener.lastMessageAt()
        if (lastMessage == 0L) {
            return
        }
        val absent = (System.currentTimeMillis() - lastMessage) > properties.binance.dataAbsenceWindow.toMillis()
        val state = tradingStatePort.read()
        if (absent && state.marketDataHealthy) {
            tradingStatePort.setMarketDataHealthy(false)
            observability.marketDataHealthy(false)
            logger.error("Market data absence detected: no kline message for {} symbols; state=DOWN", properties.symbols.size)
            properties.symbols.forEach { symbol ->
                notificationPort.send(
                    NotificationMessage.MarketDataLoss(
                        symbol = com.example.tradingbot.domain.valueobject.Symbol(symbol),
                        correlationId = "watchdog-${System.currentTimeMillis()}",
                    ),
                )
            }
        } else if (!absent && !state.marketDataHealthy) {
            tradingStatePort.setMarketDataHealthy(true)
            observability.marketDataHealthy(true)
            logger.warn("Market data recovered; state=UP")
        }
    }
}

@Component
@ConditionalOnProperty(name = ["trading.scheduling.enabled"], havingValue = "true", matchIfMissing = true)
class DailySummaryScheduler(
    private val getStatistics: GetTradingStatisticsUseCase,
    private val notificationPort: NotificationPort,
) {
    @Scheduled(cron = "0 0 0 * * *")
    fun sendSummary() {
        val s = getStatistics.getStatistics()
        notificationPort.send(
            NotificationMessage.Summary(
                text = "Closed trades: ${s.closedTrades}\n" +
                    "Net PnL: ${s.accumulatedNetPnl.toPlainString()}\n" +
                    "Win rate: ${s.winRatePct}%",
                correlationId = "summary-${java.time.LocalDate.now()}",
            ),
        )
    }
}
