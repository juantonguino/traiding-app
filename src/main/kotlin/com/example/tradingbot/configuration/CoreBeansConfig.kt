package com.example.tradingbot.configuration

import com.example.tradingbot.application.port.input.ClosePaperTradeUseCase
import com.example.tradingbot.application.port.input.DisableSignalProcessingUseCase
import com.example.tradingbot.application.port.input.EnableSignalProcessingUseCase
import com.example.tradingbot.application.port.input.EvaluateOpenTradeUseCase
import com.example.tradingbot.application.port.input.GenerateSignalUseCase
import com.example.tradingbot.application.port.input.GetOpenTradeUseCase
import com.example.tradingbot.application.port.input.GetSignalsUseCase
import com.example.tradingbot.application.port.input.GetSystemStatusUseCase
import com.example.tradingbot.application.port.input.GetTradesUseCase
import com.example.tradingbot.application.port.input.GetTradingStatisticsUseCase
import com.example.tradingbot.application.port.input.OpenPaperTradeUseCase
import com.example.tradingbot.application.port.input.ProcessClosedCandleUseCase
import com.example.tradingbot.application.port.output.ClockPort
import com.example.tradingbot.application.port.output.MarketDataPort
import com.example.tradingbot.application.port.output.NotificationPort
import com.example.tradingbot.application.port.output.ObservabilityPort
import com.example.tradingbot.application.port.output.PaperTradeRepositoryPort
import com.example.tradingbot.application.port.output.SignalRepositoryPort
import com.example.tradingbot.application.port.output.StatisticsRepositoryPort
import com.example.tradingbot.application.port.output.TradingStatePort
import com.example.tradingbot.application.port.output.TransactionPort
import com.example.tradingbot.application.service.ClosePaperTradeService
import com.example.tradingbot.application.service.DisableSignalProcessingService
import com.example.tradingbot.application.service.EmergencyStopService
import com.example.tradingbot.application.service.EnableSignalProcessingService
import com.example.tradingbot.application.service.EvaluateOpenTradeService
import com.example.tradingbot.application.service.GenerateSignalService
import com.example.tradingbot.application.service.GetOpenTradeService
import com.example.tradingbot.application.service.GetSignalsService
import com.example.tradingbot.application.service.GetSystemStatusService
import com.example.tradingbot.application.service.GetTradesService
import com.example.tradingbot.application.service.GetTradingStatisticsService
import com.example.tradingbot.application.service.OpenPaperTradeService
import com.example.tradingbot.application.service.ProcessClosedCandleService
import com.example.tradingbot.application.service.TradingConfig
import com.example.tradingbot.application.service.strategy.CandleHistory
import com.example.tradingbot.application.service.strategy.InMemoryCandleHistory
import com.example.tradingbot.application.service.strategy.NotificationMessageService
import com.example.tradingbot.application.service.strategy.SmaRsiStrategy
import com.example.tradingbot.application.service.strategy.StrategyConfig
import com.example.tradingbot.application.service.strategy.TradingStrategy
import com.example.tradingbot.domain.service.SignalEvaluator
import com.example.tradingbot.domain.service.TradeResultCalculator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal

@Configuration
class CoreBeansConfig {

    @Bean
    fun tradingConfig(properties: TradingProperties): TradingConfig = TradingConfig(
        maxOpenTrades = properties.maxOpenTrades,
        entryNotionalUsdt = properties.entryNotionalUsdt,
        feePercent = properties.feePercent,
        slippagePercent = properties.slippagePercent,
        stopLossPercent = properties.stopLossPercent,
        takeProfitPercent = properties.takeProfitPercent,
        tradeExpirationSeconds = properties.tradeExpirationSeconds,
    )

    @Bean
    fun strategyConfig(properties: TradingProperties): StrategyConfig = StrategyConfig(
        smaShortWindow = properties.smaShortWindow,
        smaLongWindow = properties.smaLongWindow,
        rsiPeriod = properties.rsiPeriod,
        rsiOversold = properties.rsiOversold,
        rsiOverbought = properties.rsiOverbought,
        stopLossPercent = properties.stopLossPercent ?: BigDecimal("0.02"),
        takeProfitPercent = properties.takeProfitPercent ?: BigDecimal("0.03"),
    )

    @Bean
    fun tradingStrategy(config: StrategyConfig): TradingStrategy = SmaRsiStrategy(config)

    @Bean
    fun candleHistory(): CandleHistory = InMemoryCandleHistory()

    @Bean
    fun tradeResultCalculator(config: TradingConfig): TradeResultCalculator =
        TradeResultCalculator(config.feePercent, config.slippagePercent)

    @Bean
    fun signalEvaluator(): SignalEvaluator = SignalEvaluator()

    @Bean
    fun notificationMessageService(notificationPort: NotificationPort): NotificationMessageService =
        NotificationMessageService(notificationPort)

    @Bean
    fun generateSignalUseCase(
        strategy: TradingStrategy,
        candleHistory: CandleHistory,
        signalRepository: SignalRepositoryPort,
        tradingStatePort: TradingStatePort,
        notifications: NotificationMessageService,
        observability: ObservabilityPort,
    ): GenerateSignalUseCase = GenerateSignalService(strategy, candleHistory, signalRepository, tradingStatePort, notifications, observability)

    @Bean
    fun openPaperTradeUseCase(
        transactionPort: TransactionPort,
        tradingStatePort: TradingStatePort,
        paperTradeRepository: PaperTradeRepositoryPort,
        signalRepository: SignalRepositoryPort,
        calculator: TradeResultCalculator,
        clockPort: ClockPort,
        config: TradingConfig,
        notifications: NotificationMessageService,
        observability: ObservabilityPort,
    ): OpenPaperTradeUseCase = OpenPaperTradeService(
        transactionPort, tradingStatePort, paperTradeRepository, signalRepository,
        calculator, clockPort, config, notifications, observability,
    )

    @Bean
    fun closePaperTradeUseCase(
        transactionPort: TransactionPort,
        tradingStatePort: TradingStatePort,
        paperTradeRepository: PaperTradeRepositoryPort,
        signalRepository: SignalRepositoryPort,
        marketDataPort: MarketDataPort,
        calculator: TradeResultCalculator,
        clockPort: ClockPort,
        notifications: NotificationMessageService,
        observability: ObservabilityPort,
    ): ClosePaperTradeUseCase = ClosePaperTradeService(
        transactionPort, tradingStatePort, paperTradeRepository, signalRepository,
        marketDataPort, calculator, clockPort, notifications, observability,
    )

    @Bean
    fun evaluateOpenTradeUseCase(
        paperTradeRepository: PaperTradeRepositoryPort,
        marketDataPort: MarketDataPort,
        closePaperTrade: ClosePaperTradeUseCase,
        clockPort: ClockPort,
        config: TradingConfig,
    ): EvaluateOpenTradeUseCase = EvaluateOpenTradeService(
        paperTradeRepository, marketDataPort, closePaperTrade, clockPort, config,
    )

    @Bean
    fun processClosedCandleUseCase(
        generateSignal: GenerateSignalUseCase,
        openPaperTrade: OpenPaperTradeUseCase,
        closePaperTrade: ClosePaperTradeUseCase,
        signalRepository: SignalRepositoryPort,
        tradingStatePort: TradingStatePort,
        paperTradeRepository: PaperTradeRepositoryPort,
        notifications: NotificationMessageService,
        signalEvaluator: SignalEvaluator,
        observability: ObservabilityPort,
    ): ProcessClosedCandleUseCase = ProcessClosedCandleService(
        generateSignal, openPaperTrade, closePaperTrade, signalRepository,
        tradingStatePort, paperTradeRepository, notifications, signalEvaluator, observability,
    )

    @Bean
    fun getOpenTradeUseCase(paperTradeRepository: PaperTradeRepositoryPort): GetOpenTradeUseCase =
        GetOpenTradeService(paperTradeRepository)

    @Bean
    fun getTradesUseCase(paperTradeRepository: PaperTradeRepositoryPort): GetTradesUseCase =
        GetTradesService(paperTradeRepository)

    @Bean
    fun getSignalsUseCase(signalRepository: SignalRepositoryPort): GetSignalsUseCase =
        GetSignalsService(signalRepository)

    @Bean
    fun getSystemStatusUseCase(
        tradingStatePort: TradingStatePort,
        getOpenTrade: GetOpenTradeUseCase,
    ): GetSystemStatusUseCase = GetSystemStatusService(tradingStatePort, getOpenTrade)

    @Bean
    fun enableSignalProcessingUseCase(
        transactionPort: TransactionPort,
        tradingStatePort: TradingStatePort,
    ): EnableSignalProcessingUseCase = EnableSignalProcessingService(transactionPort, tradingStatePort)

    @Bean
    fun disableSignalProcessingUseCase(
        transactionPort: TransactionPort,
        tradingStatePort: TradingStatePort,
    ): DisableSignalProcessingUseCase = DisableSignalProcessingService(transactionPort, tradingStatePort)

    @Bean
    fun getTradingStatisticsUseCase(statisticsRepository: StatisticsRepositoryPort): GetTradingStatisticsUseCase =
        GetTradingStatisticsService(statisticsRepository)

    @Bean
    fun emergencyStopService(
        transactionPort: TransactionPort,
        tradingStatePort: TradingStatePort,
        closePaperTrade: ClosePaperTradeUseCase,
        notificationPort: NotificationPort,
    ): EmergencyStopService = EmergencyStopService(transactionPort, tradingStatePort, closePaperTrade, notificationPort)
}
