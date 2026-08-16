package com.example.tradingbot.adapter.input.rest

import com.example.tradingbot.application.port.input.OpenPaperTradeUseCase
import com.example.tradingbot.application.port.output.MarketDataPort
import com.example.tradingbot.application.port.output.PaperTradeRepositoryPort
import com.example.tradingbot.application.port.output.SignalRepositoryPort
import com.example.tradingbot.application.port.output.TradingStatePort
import com.example.tradingbot.domain.model.CloseReason
import com.example.tradingbot.domain.valueobject.Price
import com.example.tradingbot.domain.valueobject.Symbol
import com.example.tradingbot.support.buySignal
import com.example.tradingbot.support.price
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ControlControllerIntegrationTest {

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
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var signalRepository: SignalRepositoryPort

    @Autowired
    private lateinit var tradeRepository: PaperTradeRepositoryPort

    @Autowired
    private lateinit var statePort: TradingStatePort

    @Autowired
    private lateinit var openPaperTrade: OpenPaperTradeUseCase

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

    private fun openTradeWithSignal() {
        signalRepository.save(buySignal(signalId = "ctrl-it-open"))
        openPaperTrade.open(buySignal(signalId = "ctrl-it-open"))
    }

    @Test
    fun `disable records the actor and the time`() {
        mockMvc.perform(
            post("/api/v1/control/signals/disable")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"actor":"it-tester"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.signalsEnabled").value(false))
            .andExpect(jsonPath("$.emergencyActive").value(false))
            .andExpect(jsonPath("$.signalsDisabledBy").value("it-tester"))
            .andExpect(jsonPath("$.signalsDisabledAt").isNotEmpty)

        val state = statePort.read()
        assertThat(state.signalsEnabled).isFalse()
        assertThat(state.signalsDisabledBy).isEqualTo("it-tester")
        assertThat(state.signalsDisabledAt).isNotNull()
    }

    @Test
    fun `disable without actor defaults to anonymous`() {
        mockMvc.perform(
            post("/api/v1/control/signals/disable")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"actor":null}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.signalsDisabledBy").value("anonymous"))

        assertThat(statePort.read().signalsDisabledBy).isEqualTo("anonymous")
    }

    @Test
    fun `enable restores signal processing and clears the audit fields`() {
        mockMvc.perform(
            post("/api/v1/control/signals/disable")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"actor":"it-tester"}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(post("/api/v1/control/signals/enable"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.signalsEnabled").value(true))
            .andExpect(jsonPath("$.signalsDisabledBy").isEmpty)

        val state = statePort.read()
        assertThat(state.signalsEnabled).isTrue()
        assertThat(state.signalsDisabledBy).isNull()
        assertThat(state.signalsDisabledAt).isNull()
    }

    @Test
    fun `emergency stop closes the open trade and reports its id`() {
        openTradeWithSignal()
        val open = tradeRepository.findOpenTrade()!!

        mockMvc.perform(post("/api/v1/control/emergency-stop"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("EMERGENCY_STOPPED"))
            .andExpect(jsonPath("$.closedTradeId").value(open.tradeId.value))

        val closed = tradeRepository.findByCorrelationId(open.tradeId)!!
        assertThat(closed.status.name).isEqualTo("CLOSED")
        assertThat(closed.closeReason).isEqualTo(CloseReason.EMERGENCY)
        assertThat(statePort.read().emergencyActive).isTrue()
        assertThat(statePort.read().signalsEnabled).isFalse()
    }

    @Test
    fun `emergency stop with no open trade reports none closed`() {
        mockMvc.perform(post("/api/v1/control/emergency-stop"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("EMERGENCY_STOPPED"))
            .andExpect(jsonPath("$.closedTradeId").isEmpty)

        assertThat(statePort.read().emergencyActive).isTrue()
    }
}
