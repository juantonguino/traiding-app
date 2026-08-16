package com.example.tradingbot.adapter.input.binance.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class BinanceKlineEvent(
    @JsonProperty("e") val eventType: String? = null,
    @JsonProperty("s") val symbol: String? = null,
    @JsonProperty("k") val k: BinanceKline? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BinanceKline(
    @JsonProperty("t") val openTime: Long? = null,
    @JsonProperty("T") val closeTime: Long? = null,
    @JsonProperty("s") val symbol: String? = null,
    @JsonProperty("i") val interval: String? = null,
    @JsonProperty("o") val openPrice: String? = null,
    @JsonProperty("c") val closePrice: String? = null,
    @JsonProperty("h") val highPrice: String? = null,
    @JsonProperty("l") val lowPrice: String? = null,
    @JsonProperty("v") val baseVolume: String? = null,
    @JsonProperty("x") val isClosed: Boolean? = null,
)
