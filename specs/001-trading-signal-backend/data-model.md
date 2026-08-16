# Data Model: Trading Signal Backend

**Branch**: `001-trading-signal-backend` | **Date**: 2026-08-14 | **Spec**: [spec.md](spec.md)

Relational model for MySQL 8 managed exclusively by Flyway migrations (no `ddl-auto=update`).
JPA entities live only in the persistence adapter and are mapped to/from domain models via
explicit mappers.

## Conventions

- **Money**: all monetary/price/quantity values are `DECIMAL(24,8)`. Storage scale 8, rounding `HALF_UP`.
- **Timestamps**: stored as `TIMESTAMP(6) UTC`; domain uses `Instant`. A single temporal policy: all persistence is UTC.
- **IDs**: every table has a `BIGINT` surrogate PK plus a `VARCHAR(36)` business/correlation UUID column surfaced to logs and notifications.
- **Audit**: every table has `created_at TIMESTAMP(6) NOT NULL` and `updated_at TIMESTAMP(6) NOT NULL`, maintained by the persistence adapter (not DB triggers in v1).
- **Status columns**: stored as `VARCHAR` with allowed values documented below; enums are enforced in the domain and validated in the adapter.

## Entity-Relationship Overview

```text
trading_state (1 row) 1──0..1 paper_trades.status='OPEN'
signals 1──0..1 paper_trades (open_signal_id, close_signal_id)
paper_trades 1──0..1 trading_state (open_trade_id)
```

## Table: `signals`

Every signal ever generated (BUY, SELL, HOLD), including ignored signals.

| Column | Type | Null | Notes |
|--------|------|------|-------|
| `id` | `BIGINT` AUTO_INCREMENT | NO | PK |
| `signal_id` | `VARCHAR(36)` | NO | Correlation UUID, `UNIQUE` |
| `symbol` | `VARCHAR(20)` | NO | e.g. `BTCUSDT` |
| `timeframe` | `VARCHAR(10)` | NO | e.g. `1m`, `15m`, `1h` |
| `side` | `VARCHAR(10)` | NO | `BUY` / `SELL` / `HOLD` |
| `price` | `DECIMAL(24,8)` | NO | Reference price |
| `candle_open_time` | `TIMESTAMP(6)` | NO | Candle identity (Binance `k.t`) |
| `candle_close_time` | `TIMESTAMP(6)` | NO | Candle identity (Binance `k.T`) |
| `strategy` | `VARCHAR(50)` | NO | Generating strategy id |
| `confidence` | `DECIMAL(5,2)` | YES | 0.00–100.00, when the strategy provides it |
| `reason` | `VARCHAR(255)` | NO | Human-readable explanation |
| `stop_loss` | `DECIMAL(24,8)` | YES | Optional |
| `take_profit` | `DECIMAL(24,8)` | YES | Optional |
| `status` | `VARCHAR(20)` | NO | See allowed states below |
| `ignore_reason` | `VARCHAR(50)` | YES | e.g. `GLOBAL_TRADE_ALREADY_OPEN` |
| `open_trade_id` | `BIGINT` | YES | FK → `paper_trades(id)` when used to open |
| `created_at` / `updated_at` | `TIMESTAMP(6)` | NO | Audit |

**Allowed `status`**: `GENERATED`, `ACCEPTED`, `IGNORED`, `USED_TO_OPEN`, `USED_TO_CLOSE`, `EXPIRED`.

**Idempotency / uniqueness**:
- `UNIQUE (signal_id)`.
- `UNIQUE (symbol, timeframe, candle_open_time, strategy)` — one signal per candle per strategy; a redelivered candle cannot insert a second signal (insert uses `INSERT ... ON DUPLICATE KEY UPDATE` or a guarded insert within the same transaction, returning the existing row).

**Indexes**:
- `UNIQUE idx_signals_id (signal_id)`
- `UNIQUE idx_signals_candle (symbol, timeframe, candle_open_time, strategy)`
- `INDEX idx_signals_status (status)`
- `INDEX idx_signals_symbol_time (symbol, candle_open_time)`

## Table: `paper_trades`

Simulated trades (open and closed history).

| Column | Type | Null | Notes |
|--------|------|------|-------|
| `id` | `BIGINT` AUTO_INCREMENT | NO | PK |
| `trade_id` | `VARCHAR(36)` | NO | Correlation UUID, `UNIQUE` |
| `symbol` | `VARCHAR(20)` | NO | |
| `timeframe` | `VARCHAR(10)` | NO | |
| `strategy` | `VARCHAR(50)` | NO | |
| `quantity` | `DECIMAL(24,8)` | NO | Simulated quantity |
| `entry_price` | `DECIMAL(24,8)` | NO | |
| `entry_notional` | `DECIMAL(24,8)` | NO | `entry_price * quantity` |
| `open_time` | `TIMESTAMP(6)` | NO | |
| `open_signal_id` | `BIGINT` | NO | FK → `signals(id)` |
| `stop_loss` | `DECIMAL(24,8)` | YES | Snapshot at open |
| `take_profit` | `DECIMAL(24,8)` | YES | Snapshot at open |
| `status` | `VARCHAR(10)` | NO | `OPEN` / `CLOSED` |
| `open_guard` | `VARCHAR(20)` GENERATED | NO | `IF(status='OPEN', symbol, NULL)` — see concurrency |
| `close_reason` | `VARCHAR(30)` | YES | `SELL_SIGNAL`, `STOP_LOSS`, `TAKE_PROFIT`, `MANUAL_CLOSE`, `EMERGENCY`, `EXPIRATION` |
| `exit_price` | `DECIMAL(24,8)` | YES | Set on close |
| `close_time` | `TIMESTAMP(6)` | YES | Set on close |
| `duration_seconds` | `BIGINT` | YES | `close_time - open_time` |
| `close_signal_id` | `BIGINT` | YES | FK → `signals(id)` for SELL closes |
| `gross_pnl` | `DECIMAL(24,8)` | YES | Persisted result (no recalculation later) |
| `fees` | `DECIMAL(24,8)` | YES | Entry + exit estimated commissions |
| `slippage_cost` | `DECIMAL(24,8)` | YES | Estimated slippage |
| `net_pnl` | `DECIMAL(24,8)` | YES | `gross_pnl - fees - slippage_cost` |
| `return_pct` | `DECIMAL(12,4)` | YES | `net_pnl / entry_notional * 100` |
| `result` | `VARCHAR(12)` | YES | `WIN` / `LOSS` / `BREAK_EVEN` |
| `created_at` / `updated_at` | `TIMESTAMP(6)` | NO | Audit |

**Allowed `status`**: `OPEN`, `CLOSED`.

**Concurrency guard**: `open_guard` generated column + `UNIQUE (open_guard)` enforces at the DB level that at most one `OPEN` row exists (emulates a partial unique index). The primary serialization is the `trading_state` row lock; this is the backstop.

**Indexes**:
- `UNIQUE idx_trades_id (trade_id)`
- `UNIQUE idx_trades_open_guard (open_guard)`
- `INDEX idx_trades_status (status)`
- `INDEX idx_trades_symbol_open (symbol, open_time)`
- `INDEX idx_trades_close (close_time)`
- `INDEX idx_trades_signal (open_signal_id)`

## Table: `trading_state`

Single-row global state — the source of truth for the single-open-trade rule and system mode.

| Column | Type | Null | Notes |
|--------|------|------|-------|
| `id` | `TINYINT` | NO | PK, `CHECK (id = 1)`, seeded with a single row |
| `mode` | `VARCHAR(10)` | NO | `PAPER` |
| `signals_enabled` | `BOOLEAN` | NO | Default `TRUE` |
| `open_trade_id` | `BIGINT` | YES | FK → `paper_trades(id)` |
| `open_symbol` | `VARCHAR(20)` | YES | Denormalized for notifications |
| `emergency_active` | `BOOLEAN` | NO | Default `FALSE` |
| `market_data_healthy` | `BOOLEAN` | NO | Default `FALSE` |
| `last_candle_processed_at` | `TIMESTAMP(6)` | YES | Updated by the market-data watchdog |
| `signals_disabled_by` | `VARCHAR(50)` | YES | Who/what disabled signals |
| `signals_disabled_at` | `TIMESTAMP(6)` | YES | |
| `created_at` / `updated_at` | `TIMESTAMP(6)` | NO | Audit |

**Concurrency strategy** (documented in `plan.md`): every open/close flow runs in one transaction
and first executes `SELECT ... FOR UPDATE` on this row (`WHERE id = 1`), serializing concurrent
signal processing. The row is the lock and the source of truth.

## Table: `trade_statistics_daily` (optional, v1 deferred)

Materialized daily aggregates; NOT required for v1 (statistics are computed on demand from
`paper_trades`). Introduced later without domain changes.

| Column | Type | Notes |
|--------|------|-------|
| `day` | `DATE` | Aggregation day (UTC) |
| `symbol` | `VARCHAR(20)` | |
| `timeframe` | `VARCHAR(10)` | |
| `strategy` | `VARCHAR(50)` | |
| `closed_count` | `INT` | |
| `win_count` / `loss_count` | `INT` | |
| `gross_pnl` / `fees` / `net_pnl` | `DECIMAL(24,8)` | |
| `UNIQUE (day, symbol, timeframe, strategy)` | | Idempotent upsert |

## Flyway Migrations (order)

1. `V1__create_signals.sql` — `signals` table, unique/id indexes.
2. `V2__create_paper_trades.sql` — `paper_trades` table with `open_guard` generated column, FKs, indexes.
3. `V3__create_trading_state.sql` — `trading_state` table.
4. `V4__seed_trading_state.sql` — insert the single row (`id = 1`, `mode = 'PAPER'`, `signals_enabled = TRUE`, `emergency_active = FALSE`).
5. `V5__create_trade_statistics_daily.sql` — optional materialized table (applied only if enabled).
6. `V6__add_indexes.sql` — any follow-up indexes discovered during integration tests (added via new versioned migration, never edited in place).

## Mapping Domain ↔ Persistence

| Domain model | Persistence entity | Mapper |
|---|---|---|
| `MarketCandle` | `SignalEntity.candle_*` (denormalized) / not persisted directly | `MarketCandleMapper` |
| `TradingSignal` | `SignalEntity` | `SignalEntityMapper` |
| `PaperTrade` | `PaperTradeEntity` | `PaperTradeEntityMapper` |
| `TradingState` | `TradingStateEntity` | `TradingStateEntityMapper` |
| `TradingStatistics` | computed from `PaperTradeEntity` | `StatisticsMapper` |

Rules: JPA entities never leave the persistence adapter; domain models never carry JPA
annotations; mapping is explicit in `adapter.output.persistence.mapper`.
