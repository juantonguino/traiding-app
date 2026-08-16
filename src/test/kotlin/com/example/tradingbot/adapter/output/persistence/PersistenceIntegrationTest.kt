package com.example.tradingbot.adapter.output.persistence

import com.example.tradingbot.application.port.input.OpenPaperTradeUseCase
import com.example.tradingbot.application.port.output.PaperTradeRepositoryPort
import com.example.tradingbot.application.port.output.SignalRepositoryPort
import com.example.tradingbot.application.port.output.StatisticsRepositoryPort
import com.example.tradingbot.application.port.output.TradingStatePort
import com.example.tradingbot.domain.exception.OpenTradeExistsException
import com.example.tradingbot.domain.model.SignalStatus
import com.example.tradingbot.domain.model.TradeResult
import com.example.tradingbot.domain.model.TradingState
import com.example.tradingbot.support.BTC
import com.example.tradingbot.support.buySignal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PersistenceIntegrationTest {

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

    @Autowired
    private lateinit var signalRepository: SignalRepositoryPort

    @Autowired
    private lateinit var tradeRepository: PaperTradeRepositoryPort

    @Autowired
    private lateinit var statePort: TradingStatePort

    @Autowired
    private lateinit var statisticsRepository: StatisticsRepositoryPort

    @Autowired
    private lateinit var openPaperTrade: OpenPaperTradeUseCase

    @Autowired
    private lateinit var jdbc: JdbcClient

    @BeforeEach
    fun cleanDatabase() {
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

    @Test
    fun `flyway seeds the single trading state row`() {
        val state = statePort.read()

        assertThat(state.mode).isEqualTo("PAPER")
        assertThat(state.signalsEnabled).isTrue()
        assertThat(state.emergencyActive).isFalse()
        assertThat(state.hasOpenTrade()).isFalse()
    }

    @Test
    fun `signal and trade persist and state tracks open trade`() {
        val signal = signalRepository.save(buySignal(signalId = "it-sig-1"))
        assertThat(signalRepository.findByCandleKey(signal.symbol, signal.timeframe, signal.candleOpenTime))
            .isNotNull()

        val trade = openPaperTrade.open(buySignal(signalId = "it-sig-1"))

        assertThat(tradeRepository.findByCorrelationId(trade.tradeId)).isNotNull()
        assertThat(statePort.read().hasOpenTrade()).isTrue()
        assertThat(signalRepository.findById(signal.signalId)!!.status).isEqualTo(SignalStatus.USED_TO_OPEN)

        statePort.markClosed()
        assertThat(statePort.read().hasOpenTrade()).isFalse()
    }

    @Test
    fun `statistics reflect an open trade`() {
        signalRepository.save(buySignal(signalId = "it-sig-stats"))
        openPaperTrade.open(buySignal(signalId = "it-sig-stats"))

        val stats = statisticsRepository.statistics(com.example.tradingbot.application.port.input.StatisticsFilters())

        assertThat(stats.openTrades).isEqualTo(1)
        assertThat(stats.closedTrades).isEqualTo(0)
        assertThat(stats.bySymbol).isEmpty()
    }

    @Test
    fun `only one of two concurrent opens succeeds`() {
        val signal = buySignal(signalId = "it-sig-concurrent")
        signalRepository.save(signal)

        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val results = java.util.concurrent.ConcurrentLinkedQueue<Any>()

        repeat(2) {
            executor.submit {
                ready.countDown()
                start.await()
                try {
                    val trade = openPaperTrade.open(buySignal(signalId = "it-sig-concurrent"))
                    results.add(trade.tradeId.value)
                } catch (e: OpenTradeExistsException) {
                    results.add("conflict")
                }
            }
        }

        ready.await(10, TimeUnit.SECONDS)
        start.countDown()
        executor.shutdown()
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue()

        val successes = results.filter { it != "conflict" }
        assertThat(successes).hasSize(1)
        assertThat(results).contains("conflict")
        assertThat(tradeRepository.countOpen()).isEqualTo(1)
        assertThat(statePort.read().hasOpenTrade()).isTrue()
    }

    @Test
    fun `trading state supports disable enable and emergency transitions`() {
        statePort.setSignalsEnabled(false, "it-tester")
        var state: TradingState = statePort.read()
        assertThat(state.signalsEnabled).isFalse()
        assertThat(state.signalsDisabledBy).isEqualTo("it-tester")

        statePort.setEmergencyActive()
        state = statePort.read()
        assertThat(state.emergencyActive).isTrue()
        assertThat(state.signalsEnabled).isFalse()

        statePort.clearEmergency()
        statePort.setSignalsEnabled(true, null)
        state = statePort.read()
        assertThat(state.signalsEnabled).isTrue()
        assertThat(state.emergencyActive).isFalse()
    }
}
