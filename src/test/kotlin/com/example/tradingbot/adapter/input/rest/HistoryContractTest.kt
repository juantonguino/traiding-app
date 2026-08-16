package com.example.tradingbot.adapter.input.rest

import com.example.tradingbot.application.port.input.ClosePaperTradeUseCase
import com.example.tradingbot.application.port.input.OpenPaperTradeUseCase
import com.example.tradingbot.application.port.output.MarketDataPort
import com.example.tradingbot.application.port.output.PaperTradeRepositoryPort
import com.example.tradingbot.application.port.output.SignalRepositoryPort
import com.example.tradingbot.application.port.output.TradingStatePort
import com.example.tradingbot.domain.model.CloseReason
import com.example.tradingbot.domain.model.IgnoreReasons
import com.example.tradingbot.domain.model.SignalStatus
import com.example.tradingbot.domain.model.TradeResult
import com.example.tradingbot.domain.valueobject.Price
import com.example.tradingbot.domain.valueobject.Symbol
import com.example.tradingbot.support.EPOCH
import com.example.tradingbot.support.buySignal
import com.example.tradingbot.support.price
import com.example.tradingbot.support.sellSignal
import org.hamcrest.Matchers.hasItem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HistoryContractTest {

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
        var nextPrice: Price = price("62000.0")

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
    private lateinit var closePaperTrade: ClosePaperTradeUseCase

    @Autowired
    private lateinit var jdbc: JdbcClient

    @BeforeEach
    fun setUp() {
        MutableMarketDataPort.nextPrice = price("62000.0")
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

    private fun seedHistory(): String {
        signalRepository.save(buySignal(signalId = "hist-open", candleOpenTime = EPOCH))
        val open = openPaperTrade.open(buySignal(signalId = "hist-open", candleOpenTime = EPOCH))

        signalRepository.save(
            buySignal(signalId = "hist-ignored-buy", candleOpenTime = EPOCH.plusSeconds(5400))
                .asIgnored(IgnoreReasons.SIGNALS_DISABLED),
        )
        signalRepository.save(
            sellSignal(signalId = "hist-ignored-sell", candleOpenTime = EPOCH.plusSeconds(6300))
                .asIgnored(IgnoreReasons.NO_MATCHING_OPEN_TRADE),
        )

        signalRepository.save(sellSignal(signalId = "hist-sell", candleOpenTime = EPOCH.plusSeconds(900)))
        closePaperTrade.closeBySellSignal(sellSignal(signalId = "hist-sell", candleOpenTime = EPOCH.plusSeconds(900)))

        return open.tradeId.value
    }

    @Test
    fun `signals history exposes statuses and ignore reasons`() {
        seedHistory()

        mockMvc.perform(get("/api/v1/signals"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(4))
            .andExpect(jsonPath("$.items.length()").value(4))
            .andExpect(jsonPath("$.items[?(@.signalId=='hist-open')].status", hasItem(SignalStatus.USED_TO_OPEN.name)))
            .andExpect(jsonPath("$.items[?(@.signalId=='hist-open')].symbol", hasItem("BTCUSDT")))
            .andExpect(jsonPath("$.items[?(@.signalId=='hist-sell')].status", hasItem(SignalStatus.USED_TO_CLOSE.name)))
            .andExpect(jsonPath("$.items[?(@.signalId=='hist-ignored-buy')].status", hasItem(SignalStatus.IGNORED.name)))
            .andExpect(jsonPath("$.items[?(@.signalId=='hist-ignored-buy')].ignoreReason", hasItem(IgnoreReasons.SIGNALS_DISABLED)))
            .andExpect(jsonPath("$.items[?(@.signalId=='hist-ignored-sell')].status", hasItem(SignalStatus.IGNORED.name)))
            .andExpect(jsonPath("$.items[?(@.signalId=='hist-ignored-sell')].ignoreReason", hasItem(IgnoreReasons.NO_MATCHING_OPEN_TRADE)))

        mockMvc.perform(get("/api/v1/signals").queryParam("status", "IGNORED"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(2))

        mockMvc.perform(get("/api/v1/signals").queryParam("status", "USED_TO_OPEN"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].signalId").value("hist-open"))
    }

    @Test
    fun `trades history links each trade to its opening and closing signals`() {
        val tradeId = seedHistory()

        mockMvc.perform(get("/api/v1/trades/$tradeId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CLOSED"))
            .andExpect(jsonPath("$.closeReason").value(CloseReason.SELL_SIGNAL.name))
            .andExpect(jsonPath("$.result").value(TradeResult.WIN.name))
            .andExpect(jsonPath("$.openSignalId").value("hist-open"))
            .andExpect(jsonPath("$.closeSignalId").value("hist-sell"))
    }

    @Test
    fun `history reconstruction - trade outcome is derivable from its signals`() {
        val tradeId = seedHistory()

        val tradeJson = mockMvc.perform(get("/api/v1/trades/$tradeId"))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        mockMvc.perform(get("/api/v1/signals"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[?(@.signalId=='hist-open')].price", hasItem("60500.00000000")))
            .andExpect(jsonPath("$.items[?(@.signalId=='hist-open')].takeProfit", hasItem("63000.00000000")))
            .andExpect(jsonPath("$.items[?(@.signalId=='hist-open')].stopLoss", hasItem("59000.00000000")))
            .andExpect(jsonPath("$.items[?(@.signalId=='hist-sell')].price", hasItem("62000.00000000")))
            .andExpect(jsonPath("$.items[?(@.signalId=='hist-open')].strategy", hasItem("sma-rsi")))
            .andExpect(jsonPath("$.items[?(@.signalId=='hist-open')].timeframe", hasItem("15m")))

        val trade = tradeRepository.findByCorrelationId(com.example.tradingbot.domain.valueobject.TradeId(tradeId))!!
        val openSignal = signalRepository.findById(com.example.tradingbot.domain.valueobject.SignalId(trade.openSignalId.value))!!
        val closeSignal = signalRepository.findById(com.example.tradingbot.domain.valueobject.SignalId(trade.closeSignalId!!.value))!!

        assertThat(trade.status.name).isEqualTo("CLOSED")
        assertThat(trade.closeReason).isEqualTo(CloseReason.SELL_SIGNAL)
        assertThat(trade.entryPrice.value).isEqualByComparingTo(openSignal.price.value)
        assertThat(trade.exitPrice!!.value).isEqualByComparingTo(closeSignal.price.value)
        assertThat(trade.result).isEqualTo(TradeResult.WIN)
        assertThat(openSignal.status).isEqualTo(SignalStatus.USED_TO_OPEN)
        assertThat(closeSignal.status).isEqualTo(SignalStatus.USED_TO_CLOSE)
        assertThat(tradeJson).contains("\"openSignalId\":\"hist-open\"")
    }

    @Test
    fun `signals history is filterable by symbol and side`() {
        seedHistory()

        mockMvc.perform(get("/api/v1/signals").queryParam("symbol", "BTCUSDT"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(4))

        mockMvc.perform(get("/api/v1/signals").queryParam("side", "SELL"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(2))

        mockMvc.perform(get("/api/v1/signals").queryParam("symbol", "ETHUSDT"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(0))
    }
}
