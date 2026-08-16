# API Contracts: Trading Signal Backend

**Branch**: `001-trading-signal-backend` | **Date**: 2026-08-14 | **Spec**: [spec.md](../spec.md) | **Data model**: [data-model.md](../data-model.md)

REST endpoints for **consultation and safe control only**. The system is PAPER TRADING: there is
no endpoint that sends real orders to Binance, and none will be added.

Base path: `/api/v1`. Content type: `application/json`. Timestamps: ISO-8601 UTC (`Instant`).

## Endpoints for v1

| Method | Path | Purpose | Phase |
|--------|------|---------|-------|
| `GET` | `/api/v1/health` | Liveness/health summary | v1 |
| `GET` | `/api/v1/status` | System state (mode, signals enabled, open trade, data health) | v1 |
| `GET` | `/api/v1/trades/open` | Current open trade (if any) | v1 |
| `GET` | `/api/v1/trades` | Closed trades, filterable by symbol/strategy/timeframe/period | v1 |
| `GET` | `/api/v1/trades/{tradeId}` | Single trade by correlation `trade_id` | v1 |
| `GET` | `/api/v1/signals` | Signals, filterable by symbol/status/period | v1 |
| `GET` | `/api/v1/statistics` | Accumulated statistics | v1 |
| `POST` | `/api/v1/control/signals/disable` | Disable signal generation (records actor + time) | v1 |
| `POST` | `/api/v1/control/signals/enable` | Enable signal generation | v1 |
| `POST` | `/api/v1/control/emergency-stop` | Emergency: close open trade, block new entries | v1 |
| `POST` | `/api/v1/trades/open/close` | Authorized manual close of the current open trade | v1 |
| `POST` | `/api/v1/trades/{tradeId}/close` | Authorized manual close by id | Postponed (redundant with `/trades/open/close` for v1) |

### `GET /api/v1/health`

```json
{
  "status": "UP",
  "checks": {
    "mysql": "UP",
    "binance": "UP",
    "telegram": "UP"
  }
}
```

### `GET /api/v1/status`

```json
{
  "mode": "PAPER",
  "signalsEnabled": true,
  "emergencyActive": false,
  "openTrade": null,
  "marketDataHealthy": true,
  "lastCandleProcessedAt": "2026-08-14T12:00:00Z",
  "signalsDisabledBy": null,
  "signalsDisabledAt": null
}
```

### `GET /api/v1/trades/open`

```json
{
  "tradeId": "a1b2c3d4-...",
  "symbol": "BTCUSDT",
  "timeframe": "15m",
  "strategy": "sma-rsi",
  "quantity": "0.00500000",
  "entryPrice": "65500.00000000",
  "openTime": "2026-08-14T11:30:00Z",
  "stopLoss": "63000.00000000",
  "takeProfit": "68000.00000000",
  "status": "OPEN",
  "openSignalId": "9f8e7d6c-..."
}
```

### `GET /api/v1/trades` (closed; `?symbol=BTCUSDT&strategy=sma-rsi&from=2026-08-01&to=2026-08-14`)

```json
{
  "items": [
    {
      "tradeId": "b2c3d4e5-...",
      "symbol": "BTCUSDT",
      "strategy": "sma-rsi",
      "entryPrice": "64000.00000000",
      "exitPrice": "66000.00000000",
      "quantity": "0.00500000",
      "openTime": "2026-08-13T09:00:00Z",
      "closeTime": "2026-08-13T12:15:00Z",
      "durationSeconds": 11700,
      "closeReason": "SELL_SIGNAL",
      "grossPnl": "10.00000000",
      "fees": "0.32500000",
      "slippageCost": "0.26000000",
      "netPnl": "9.41500000",
      "returnPct": "2.9400",
      "result": "WIN"
    }
  ],
  "total": 1
}
```

### `GET /api/v1/trades/{tradeId}` — a single trade, same shape as items above (404 if unknown).

### `GET /api/v1/signals` (`?symbol=&status=&side=&from=&to=`)

```json
{
  "items": [
    {
      "signalId": "9f8e7d6c-...",
      "symbol": "BTCUSDT",
      "timeframe": "15m",
      "side": "BUY",
      "price": "65500.00000000",
      "candleOpenTime": "2026-08-14T11:30:00Z",
      "strategy": "sma-rsi",
      "confidence": 72.5,
      "reason": "SMA crossover with RSI below 30",
      "stopLoss": "63000.00000000",
      "takeProfit": "68000.00000000",
      "status": "ACCEPTED",
      "ignoreReason": null
    }
  ],
  "total": 1
}
```

### `GET /api/v1/statistics`

```json
{
  "closedTrades": 12,
  "openTrades": 1,
  "accumulatedGrossPnl": "45.12000000",
  "accumulatedFees": "3.80000000",
  "accumulatedSlippage": "3.10000000",
  "accumulatedNetPnl": "38.22000000",
  "winRatePct": "58.33",
  "averageGain": "6.50000000",
  "averageLoss": "-2.10000000",
  "maxDrawdown": "-4.20000000",
  "bySymbol": { "BTCUSDT": { "netPnl": "38.22000000", "winRatePct": "58.33" } },
  "byStrategy": { "sma-rsi": { "netPnl": "38.22000000" } },
  "byTimeframe": { "15m": { "netPnl": "38.22000000" } }
}
```

### `POST /api/v1/control/signals/disable` / `enable`

Request body (disable): `{ "actor": "operator@example.com" }` — required for disable, recorded in
`signals_disabled_by` / `signals_disabled_at`. Response: current `status` object (200).

### `POST /api/v1/control/emergency-stop`

No body. Closes the open trade (reason `EMERGENCY`), sets `emergency_active = true`, disables
signals. Response: `{ "status": "EMERGENCY_STOPPED", "closedTradeId": "..." }`.

### `POST /api/v1/trades/open/close`

Body: `{ "reason": "MANUAL_CLOSE", "actor": "operator@example.com" }`. Closes the current open
trade. Response: the closed trade object (200), or `404 { "code": "NO_OPEN_TRADE" }`.

## Error envelope

```json
{
  "timestamp": "2026-08-14T12:00:00Z",
  "status": 400,
  "code": "INVALID_ARGUMENT",
  "message": "reason must be one of MANUAL_CLOSE",
  "requestId": "correlation-id"
}
```

Error codes: `INVALID_ARGUMENT`, `NO_OPEN_TRADE`, `SIGNALS_DISABLED`, `EMERGENCY_ACTIVE`,
`NOT_FOUND`, `INTERNAL_ERROR`. Errors never include secrets or tokens.

## Constraints for controllers

- Validate inputs; use dedicated request/response DTOs in `adapter.input.rest`.
- Invoke input ports only; never contain business rules, PnL math, or direct DB access.
- Never return JPA entities or domain internals; always map to DTOs.
- Health endpoints expose only aggregate UP/DOWN status plus per-check status (no secrets).
