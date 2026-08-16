package com.example.tradingbot.adapter.input.rest

import com.example.tradingbot.adapter.input.rest.dto.DtoMappers
import com.example.tradingbot.adapter.input.rest.dto.DisableSignalsRequest
import com.example.tradingbot.adapter.input.rest.dto.EmergencyStopResponse
import com.example.tradingbot.adapter.input.rest.dto.ManualCloseRequest
import com.example.tradingbot.adapter.input.rest.dto.OpenTradeResponse
import com.example.tradingbot.adapter.input.rest.dto.StatusResponse
import com.example.tradingbot.adapter.input.rest.dto.TradeResponse
import com.example.tradingbot.adapter.input.rest.dto.TradesResponse
import com.example.tradingbot.application.port.input.ClosePaperTradeUseCase
import com.example.tradingbot.application.port.input.DisableSignalProcessingUseCase
import com.example.tradingbot.application.port.input.EnableSignalProcessingUseCase
import com.example.tradingbot.application.port.input.GetOpenTradeUseCase
import com.example.tradingbot.application.port.input.GetSignalsUseCase
import com.example.tradingbot.application.port.input.GetTradesUseCase
import com.example.tradingbot.application.port.input.GetTradingStatisticsUseCase
import com.example.tradingbot.application.port.input.SignalFilters
import com.example.tradingbot.application.port.input.StatisticsFilters
import com.example.tradingbot.application.port.input.TradeFilters
import com.example.tradingbot.application.service.EmergencyStopService
import com.example.tradingbot.domain.exception.NotFoundException
import com.example.tradingbot.domain.model.CloseReason
import com.example.tradingbot.domain.valueobject.Symbol
import com.example.tradingbot.domain.valueobject.TradeId
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/v1")
class TradeAndStatsController(
    private val getOpenTrade: GetOpenTradeUseCase,
    private val getTrades: GetTradesUseCase,
    private val getSignals: GetSignalsUseCase,
    private val closePaperTrade: ClosePaperTradeUseCase,
    private val getStatistics: GetTradingStatisticsUseCase,
    private val disableSignals: DisableSignalProcessingUseCase,
    private val enableSignals: EnableSignalProcessingUseCase,
    private val emergencyStop: EmergencyStopService,
) {

    @GetMapping("/trades/open")
    fun openTrade(): ResponseEntity<OpenTradeResponse> {
        val trade = getOpenTrade.getOpenTrade() ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(DtoMappers.toOpenTrade(trade))
    }

    @GetMapping("/trades")
    fun trades(
        @RequestParam(required = false) symbol: String?,
        @RequestParam(required = false) strategy: String?,
        @RequestParam(required = false) timeframe: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
    ): TradesResponse {
        val items = getTrades.search(
            TradeFilters(
                symbol = symbol?.let { Symbol(it.uppercase()) },
                strategy = strategy,
                timeframe = timeframe,
                from = from?.let(Instant::parse),
                to = to?.let(Instant::parse),
            ),
        ).map(DtoMappers::toTrade)
        return TradesResponse(items = items, total = items.size.toLong())
    }

    @GetMapping("/trades/{tradeId}")
    fun trade(@PathVariable tradeId: String): ResponseEntity<TradeResponse> {
        val trade = getTrades.getByTradeId(TradeId(tradeId))
            ?: throw NotFoundException("Trade", tradeId)
        return ResponseEntity.ok(DtoMappers.toTrade(trade))
    }

    @GetMapping("/signals")
    fun signals(
        @RequestParam(required = false) symbol: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) side: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
    ): com.example.tradingbot.adapter.input.rest.dto.SignalsResponse {
        val items = getSignals.search(
            SignalFilters(
                symbol = symbol?.let { Symbol(it.uppercase()) },
                status = status,
                side = side,
                from = from?.let(Instant::parse),
                to = to?.let(Instant::parse),
            ),
        ).map(DtoMappers::toSignal)
        return com.example.tradingbot.adapter.input.rest.dto.SignalsResponse(items = items, total = items.size.toLong())
    }

    @GetMapping("/statistics")
    fun statistics(
        @RequestParam(required = false) symbol: String?,
        @RequestParam(required = false) strategy: String?,
        @RequestParam(required = false) timeframe: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
    ) = DtoMappers.toStatistics(
        getStatistics.getStatistics(
            StatisticsFilters(
                symbol = symbol?.let { Symbol(it.uppercase()) },
                strategy = strategy,
                timeframe = timeframe,
                from = from?.let(Instant::parse),
                to = to?.let(Instant::parse),
            ),
        ),
    )

    @PostMapping("/trades/open/close")
    fun manualClose(@Valid @RequestBody request: ManualCloseRequest): ResponseEntity<TradeResponse> {
        val reason = CloseReason.valueOf(request.reason)
        if (reason != CloseReason.MANUAL_CLOSE) {
            throw IllegalArgumentException("reason must be MANUAL_CLOSE")
        }
        val closed = closePaperTrade.closeCurrent(reason, actor = request.actor)
        return ResponseEntity.ok(DtoMappers.toTrade(closed))
    }

    @PostMapping("/control/signals/disable")
    fun disableSignals(@RequestBody request: DisableSignalsRequest): StatusResponse {
        val state = disableSignals.disable(request.actor ?: "anonymous")
        return toStatus(state)
    }

    @PostMapping("/control/signals/enable")
    fun enableSignals(): StatusResponse = toStatus(enableSignals.enable())

    @PostMapping("/control/emergency-stop")
    fun emergencyStop(): EmergencyStopResponse {
        val closedTradeId = emergencyStop.stop()
        return EmergencyStopResponse(status = "EMERGENCY_STOPPED", closedTradeId = closedTradeId)
    }

    private fun toStatus(state: com.example.tradingbot.domain.model.TradingState): StatusResponse = StatusResponse(
        mode = state.mode,
        signalsEnabled = state.signalsEnabled,
        emergencyActive = state.emergencyActive,
        openTrade = null,
        marketDataHealthy = state.marketDataHealthy,
        lastCandleProcessedAt = state.lastCandleProcessedAt,
        signalsDisabledBy = state.signalsDisabledBy,
        signalsDisabledAt = state.signalsDisabledAt,
    )
}
