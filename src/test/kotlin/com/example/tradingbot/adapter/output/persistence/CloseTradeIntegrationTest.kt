package com.example.tradingbot.adapter.output.persistence

import com.example.tradingbot.application.port.input.ClosePaperTradeUseCase
import com.example.tradingbot.application.port.input.EvaluateOpenTradeUseCase
import com.example.tradingbot.application.port.input.GetTradingStatisticsUseCase
import com.example.tradingbot.application.port.input.OpenPaperTradeUseCase
import com.example.tradingbot.application.port.output.MarketDataPort
import com.example.tradingbot.application.port.output.PaperTradeRepositoryPort
import com.example.tradingbot.application.port.output.SignalRepositoryPort
import com.example.tradingbot.application.port.output.TradingStatePort
import com.example.tradingbot.application.service.EmergencyStopService
import com.example.tradingbot.domain.model.CloseReason
import com.example.tradingbot.domain.model.SignalStatus
import com.example.tradingbot.domain.model.TradeResult
import com.example.tradingbot.domain.valueobject.Price
import com.example.tradingbot.domain.valueobject.Symbol
import com.example.tradingbot.support.buySignal
import com.example.tradingbot.support.price
import com.example.tradingbot.support.sellSignal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class CloseTradeIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val mysql: MySQLContainer<*> = MySQLContainer<Nothing>("mysql:8.4").apply {
            withDatabaseName("trading_bot_test")
            withUsername("trading")
            withPassword("trading")
        }

        @JvmStatic
        @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }

    object MutableMarketDataPort : MarketDataPort {
        @Volatile
        var nextPrice: Price = price("63000.0")

        override fun currentPrice(symbol: Symbol): Price = nextPrice
    }

    @TestConfiguration
    class MarketDataStubConfig {
        @Bean
        @Primary
        fun testMarketDataPort(): MarketDataPort = MutableMarketDataPort
    }

    @Autowired
    private lateinit var signalRepository: SignalRepositoryPort

    @Autowired
    private lateinit var tradeRepository: PaperTradeRepositoryPort

    @Autowired
    private lateinit var statePort: TradingStatePort

    @Autowired
    private lateinit var openPaperTrade: OpenPaperTradeUseCase

    @Autowired
    private lateinit var closePaperTrade: ClosePaperTradeUseCase

    @Autowired
    private lateinit var evaluateOpenTrade: EvaluateOpenTradeUseCase

    @Autowired
    private lateinit var emergencyStop: EmergencyStopService

    @Autowired
    private lateinit var statistics: GetTradingStatisticsUseCase

    @Autowired
    private lateinit var jdbc: JdbcClient

    @BeforeEach
    fun setUp() {
        MutableMarketDataPort.nextPrice = price("63000.0")
        jdbc.sql("SET FOREIGN_KEY_CHECKS = 0").update()
        jdbc.sql("TRUNCATE TABLE trade_statistics_daily").update()
        jdbc.sql("TRUNCATE TABLE paper_trades").update()
        jdbc.sql("TRUNCATE TABLE signals").update()
        jdbc.sql("SET FOREIGN_KEY_CHECKS = 1").update()
        jdbc.sql(
            """UPDATE trading_state
               SET open_trade_id = NULL, open_symbol = NULL, signals_enabled = TRUE,
                   emergency_active = FALSE, market_data_healthy = FALSE,
                   last_candle_processed_at = NULL, signals_disabled_by = NULL,
                   signals_disabled_at = NULL
               WHERE id = 1""",
        ).update()
    }

    private fun openWithSignal(): com.example.tradingbot.domain.model.PaperTrade {
        signalRepository.save(buySignal(signalId = "close-it-open"))
        return openPaperTrade.open(buySignal(signalId = "close-it-open"))
    }

    @Test
    fun `sell signal closes the open trade and persists the result`() {
        openWithSignal()
        signalRepository.save(sellSignal(signalId = "close-it-sell"))

        val closed = closePaperTrade.closeBySellSignal(sellSignal(signalId = "close-it-sell"))

        assertThat(closed).isNotNull()
        val persisted = tradeRepository.findByCorrelationId(closed!!.tradeId)!!
        assertThat(persisted.status.name).isEqualTo("CLOSED")
        assertThat(persisted.closeReason).isEqualTo(CloseReason.SELL_SIGNAL)
        assertThat(persisted.exitPrice!!.value).isEqualByComparingTo("62000.0")
        assertThat(persisted.netPnl).isNotNull()
        assertThat(persisted.result).isEqualTo(TradeResult.WIN)
        assertThat(signalRepository.findById(com.example.tradingbot.domain.valueobject.SignalId("close-it-sell"))!!.status)
            .isEqualTo(SignalStatus.USED_TO_CLOSE)
        assertThat(statePort.read().hasOpenTrade()).isFalse()
        assertThat(statistics.getStatistics().closedTrades).isEqualTo(1)
    }

    @Test
    fun `manual close with explicit price persists the closed trade`() {
        val open = openWithSignal()

        val closed = closePaperTrade.closeCurrent(
            reason = CloseReason.MANUAL_CLOSE,
            exitPrice = price("61500.0"),
            actor = "tester",
        )

        val persisted = tradeRepository.findByCorrelationId(closed.tradeId)!!
        assertThat(persisted.closeReason).isEqualTo(CloseReason.MANUAL_CLOSE)
        assertThat(persisted.exitPrice!!.value).isEqualByComparingTo("61500.0")
        assertThat(persisted.closeTime).isNotNull()
        assertThat(open.tradeId).isEqualTo(persisted.tradeId)
    }

    @Test
    fun `stop loss evaluation closes at the stop loss price`() {
        openWithSignal()
        MutableMarketDataPort.nextPrice = price("58500.0")

        evaluateOpenTrade.evaluate(Instant.now())

        val closed = tradeRepository.search(com.example.tradingbot.application.port.input.TradeFilters()).single()
        assertThat(closed.status.name).isEqualTo("CLOSED")
        assertThat(closed.closeReason).isEqualTo(CloseReason.STOP_LOSS)
        assertThat(closed.exitPrice!!.value).isEqualByComparingTo("59000.0")
        assertThat(closed.result).isEqualTo(TradeResult.LOSS)
    }

    @Test
    fun `take profit evaluation closes at the take profit price`() {
        openWithSignal()
        MutableMarketDataPort.nextPrice = price("64000.0")

        evaluateOpenTrade.evaluate(Instant.now())

        val closed = tradeRepository.search(com.example.tradingbot.application.port.input.TradeFilters()).single()
        assertThat(closed.status.name).isEqualTo("CLOSED")
        assertThat(closed.closeReason).isEqualTo(CloseReason.TAKE_PROFIT)
        assertThat(closed.exitPrice!!.value).isEqualByComparingTo("63000.0")
        assertThat(closed.result).isEqualTo(TradeResult.WIN)
    }

    @Test
    fun `emergency stop closes the open trade and persists the result`() {
        openWithSignal()

        val closedTradeId = emergencyStop.stop()

        assertThat(closedTradeId).isNotNull()
        val persisted = tradeRepository.findByCorrelationId(com.example.tradingbot.domain.valueobject.TradeId(closedTradeId!!))!!
        assertThat(persisted.status.name).isEqualTo("CLOSED")
        assertThat(persisted.closeReason).isEqualTo(CloseReason.EMERGENCY)
        assertThat(persisted.netPnl).isNotNull()
        assertThat(statePort.read().emergencyActive).isTrue()
        assertThat(statePort.read().signalsEnabled).isFalse()
    }

    @Test
    fun `emergency stop with no open trade reports nothing closed`() {
        val closedTradeId = emergencyStop.stop()

        assertThat(closedTradeId).isNull()
        assertThat(statePort.read().emergencyActive).isTrue()
    }
}
