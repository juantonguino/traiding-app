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
import org.hamcrest.Matchers.hasKey
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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
class TradesContractTest {

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

    private fun openTrade(): com.example.tradingbot.domain.model.PaperTrade {
        signalRepository.save(buySignal(signalId = "trades-it-open"))
        return openPaperTrade.open(buySignal(signalId = "trades-it-open"))
    }

    private fun manualClose(): String {
        val open = openTrade()
        MutableMarketDataPort.nextPrice = price("61500.0")
        mockMvc.perform(
            post("/api/v1/trades/open/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"actor":"contract-tester"}"""),
        ).andExpect(status().isOk)
        return open.tradeId.value
    }

    @Test
    fun `closed trades list returns the trade DTO shape`() {
        val tradeId = manualClose()

        mockMvc.perform(get("/api/v1/trades"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].tradeId").value(tradeId))
            .andExpect(jsonPath("$.items[0].symbol").value("BTCUSDT"))
            .andExpect(jsonPath("$.items[0].timeframe").value("15m"))
            .andExpect(jsonPath("$.items[0].strategy").value("sma-rsi"))
            .andExpect(jsonPath("$.items[0].quantity").value("0.00165289"))
            .andExpect(jsonPath("$.items[0].entryPrice").value("60500.00000000"))
            .andExpect(jsonPath("$.items[0].exitPrice").value("61500.00000000"))
            .andExpect(jsonPath("$.items[0].openTime").isNotEmpty)
            .andExpect(jsonPath("$.items[0].closeTime").isNotEmpty)
            .andExpect(jsonPath("$.items[0].durationSeconds").isNotEmpty)
            .andExpect(jsonPath("$.items[0].closeReason").value(CloseReason.MANUAL_CLOSE.name))
            .andExpect(jsonPath("$.items[0].grossPnl").isNotEmpty)
            .andExpect(jsonPath("$.items[0].netPnl").isNotEmpty)
            .andExpect(jsonPath("$.items[0].returnPct").isNotEmpty)
            .andExpect(jsonPath("$.items[0].result").value("WIN"))
            .andExpect(jsonPath("$.items[0].status").value("CLOSED"))
            .andExpect(jsonPath("$.items[0].openSignalId").value("trades-it-open"))
            .andExpect(jsonPath("$.items[0].closeSignalId").isEmpty)
    }

    @Test
    fun `single open trade by id returns the trade DTO shape`() {
        val open = openTrade()

        mockMvc.perform(get("/api/v1/trades/${open.tradeId.value}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tradeId").value(open.tradeId.value))
            .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
            .andExpect(jsonPath("$.quantity").value("0.00165289"))
            .andExpect(jsonPath("$.entryPrice").value("60500.00000000"))
            .andExpect(jsonPath("$.status").value("OPEN"))
    }

    @Test
    fun `closed trade returns the full result shape`() {
        val tradeId = manualClose()

        mockMvc.perform(get("/api/v1/trades/$tradeId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CLOSED"))
            .andExpect(jsonPath("$.closeReason").value(CloseReason.MANUAL_CLOSE.name))
            .andExpect(jsonPath("$.exitPrice").value("61500.00000000"))
            .andExpect(jsonPath("$.closeTime").isNotEmpty)
            .andExpect(jsonPath("$.durationSeconds").isNotEmpty)
            .andExpect(jsonPath("$.grossPnl").isNotEmpty)
            .andExpect(jsonPath("$.fees").isNotEmpty)
            .andExpect(jsonPath("$.slippageCost").isNotEmpty)
            .andExpect(jsonPath("$.netPnl").isNotEmpty)
            .andExpect(jsonPath("$.returnPct").isNotEmpty)
            .andExpect(jsonPath("$.result").value("WIN"))
    }

    @Test
    fun `statistics returns the aggregate shape with buckets`() {
        manualClose()

        mockMvc.perform(get("/api/v1/statistics"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.closedTrades").value(1))
            .andExpect(jsonPath("$.openTrades").value(0))
            .andExpect(jsonPath("$.accumulatedGrossPnl").isNotEmpty)
            .andExpect(jsonPath("$.accumulatedFees").isNotEmpty)
            .andExpect(jsonPath("$.accumulatedSlippage").isNotEmpty)
            .andExpect(jsonPath("$.accumulatedNetPnl").isNotEmpty)
            .andExpect(jsonPath("$.winRatePct").isNotEmpty)
            .andExpect(jsonPath("$.averageGain").isNotEmpty)
            .andExpect(jsonPath("$.averageLoss").isNotEmpty)
            .andExpect(jsonPath("$.maxDrawdown").isNotEmpty)
            .andExpect(jsonPath("$.bySymbol", hasKey("BTCUSDT")))
            .andExpect(jsonPath("$.bySymbol.BTCUSDT.closedTrades").value(1))
            .andExpect(jsonPath("$.bySymbol.BTCUSDT.netPnl").isNotEmpty)
            .andExpect(jsonPath("$.bySymbol.BTCUSDT.winRatePct").isNotEmpty)
            .andExpect(jsonPath("$.byStrategy", hasKey("sma-rsi")))
            .andExpect(jsonPath("$.byTimeframe", hasKey("15m")))
    }

    @Test
    fun `trades filter by symbol`() {
        manualClose()

        mockMvc.perform(get("/api/v1/trades").queryParam("symbol", "BTCUSDT"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))

        mockMvc.perform(get("/api/v1/trades").queryParam("symbol", "ETHUSDT"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.items.length()").value(0))
    }

    @Test
    fun `unknown trade returns the error DTO shape`() {
        mockMvc.perform(get("/api/v1/trades/does-not-exist"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").isNotEmpty)
            .andExpect(jsonPath("$.timestamp").isNotEmpty)
    }
}
