# Implementation Plan: Trading Signal Backend

**Branch**: `001-trading-signal-backend` | **Date**: 2026-08-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-trading-signal-backend/spec.md`

## Summary

Build a PAPER TRADING-only backend (Kotlin + Spring Boot, hexagonal architecture) that consumes
public Binance Spot kline streams, processes closed candles, generates BUY/SELL/HOLD signals from
a replaceable strategy, simulates trades with at most one open trade globally, computes auditable
financial results, notifies via Telegram, and exposes REST endpoints for consultation and safe
control. Research: [research.md](research.md); data model: [data-model.md](data-model.md); API:
[contracts/api.md](contracts/api.md); run guide: [quickstart.md](quickstart.md).

## Technical Context

**Language/Version**: Kotlin 2.3.x (JVM), Java 21 (LTS)

**Primary Dependencies**: Spring Boot 4.1.x (Spring Framework 7); starters: `web`, `data-jpa`,
`webflux` (for `WebClient`), `websocket`, `actuator`, `validation`, `flyway`, `micrometer`;
`kotlin-spring` + `kotlin-jpa` plugins; Gradle 9.3 Kotlin DSL; ArchUnit; JUnit 5, MockK,
Testcontainers (MySQL 8.4)

**Storage**: MySQL 8.4 (Docker Compose), schema owned by Flyway (no `ddl-auto=update`)

**Testing**: JUnit 5 + MockK (unit), Spring Boot Test + Testcontainers (integration),
ArchUnit (architecture), MockWebServer/WireMock (Telegram HTTP), simulated WebSocket server
(Binance adapter)

**Target Platform**: Linux containers via Docker Compose; local dev on macOS/Windows (Docker)

**Project Type**: Modular monolith — single Spring Boot application with hexagonal package
structure (domain / application / adapter / configuration)

**Performance Goals**: N/A for v1 (event-driven, one signal per closed candle per symbol;
throughput driven by Binance stream cadence, not user load)

**Constraints**: PAPER TRADING only; max 1 open trade globally; no real orders; no private
credentials; secrets via environment variables only; domain framework-free; financial math in
`BigDecimal` only; concurrency-safe single-open-trade rule; idempotent candle/signal/trade
processing

**Scale/Scope**: v1 = 1–few symbols, 1 timeframe, 1 replaceable strategy; single-user;
~10 REST endpoints; ~4 DB tables; 1 app service + MySQL in Compose

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Constitution rule | Status | Evidence in this plan |
|---|---|---|
| Hexagonal architecture; dependencies point inward | PASS | Domain/application/adapter/configuration split; `domain` imports no frameworks; adapters depend on ports only (see Project Structure + Architecture Decisions) |
| Domain independent of Spring/JPA/MySQL/Binance/Telegram/WebClient/Reactor | PASS | Domain uses plain Kotlin + `BigDecimal` + `Instant`; ports defined in `application.port`; adapters in `adapter.output.*` |
| Input ports = use cases; output ports = capabilities | PASS | 10 input ports and 8 output ports listed below |
| PAPER TRADING only; no real orders; public data only | PASS | `TRADING_MODE=PAPER`, `ALLOW_REAL_ORDERS=false` defaults; no order-execution endpoint or adapter |
| At most one open trade, race-safe, transactional | PASS | `trading_state` single row + `SELECT ... FOR UPDATE` + unique `open_guard` column (data-model.md) |
| Secrets via env vars; never in code/logs/HTTP | PASS | Token/chatId only from env; adapter logs without token; error envelope has no secrets |
| Financial math in `BigDecimal`, scale/rounding defined | PASS | `DECIMAL(24,8)`, HALF_UP, formulas fixed in Architecture Decisions; `Double` forbidden |
| Flyway; no `ddl-auto=update` | PASS | Versioned migrations V1–V6 (data-model.md) |
| Domain testable without Spring; tests detect architecture violations | PASS | Pure unit tests for domain + ArchUnit suite (Testing Strategy) |
| Telegram/MySQL/Binance health + structured logs + metrics | PASS | Actuator health indicators + Micrometer + JSON logs (Observability) |

No violations requiring justification → **Complexity Tracking table is intentionally left empty.**

## Project Structure

### Documentation (this feature)

```text
specs/001-trading-signal-backend/
├── plan.md              # This file
├── research.md          # Version/compat & integration research
├── data-model.md        # Relational model, Flyway, mapping
├── quickstart.md        # Validation / run guide
├── contracts/
│   └── api.md           # REST endpoints & DTOs
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Created later by /speckit.tasks
```

### Source Code (repository root)

```text
.
├── build.gradle.kts            # Kotlin 2.3.x, Spring Boot 4.1.x, Gradle 9.3 (version catalog)
├── settings.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── Dockerfile                  # multi-stage: gradle build → eclipse-temurin:21-jre
├── docker-compose.yml          # app + mysql:8.4 (healthcheck, volume, network)
├── .env.example                # documented vars; real .env is gitignored
└── src/
    ├── main/
    │   ├── kotlin/com/example/tradingbot/
    │   │   ├── TradingBotApplication.kt
    │   │   ├── domain/
    │   │   │   ├── model/        (MarketCandle, TradingSignal, PaperTrade,
    │   │   │   │                  TradingStatistics, TradingState,
    │   │   │   │                  SignalSide, SignalStatus, TradeStatus,
    │   │   │   │                  TradeResult, CloseReason)
    │   │   │   ├── valueobject/  (Symbol, Price, Quantity, Percentage, Money,
    │   │   │   │                  Timeframe, TradeId, SignalId, Commission, Slippage)
    │   │   │   ├── service/      (TradeResultCalculator, SignalEvaluator)
    │   │   │   ├── event/        (SignalGeneratedEvent, TradeOpenedEvent, TradeClosedEvent)
    │   │   │   └── exception/    (OpenTradeExistsException, NoOpenTradeException, ...)
    │   │   ├── application/
    │   │   │   ├── port/input/   (ProcessClosedCandleUseCase, GenerateSignalUseCase,
    │   │   │   │                  OpenPaperTradeUseCase, ClosePaperTradeUseCase,
    │   │   │   │                  EvaluateOpenTradeUseCase, GetOpenTradeUseCase,
    │   │   │   │                  GetTradingStatisticsUseCase,
    │   │   │   │                  EnableSignalProcessingUseCase,
    │   │   │   │                  DisableSignalProcessingUseCase,
    │   │   │   │                  GetSystemStatusUseCase)
    │   │   │   ├── port/output/  (MarketDataPort, SignalRepositoryPort,
    │   │   │   │                  PaperTradeRepositoryPort, TradingStatePort,
    │   │   │   │                  NotificationPort, StatisticsRepositoryPort,
    │   │   │   │                  ClockPort, TransactionPort)
    │   │   │   └── service/      (application services implementing the use cases)
    │   │   ├── adapter/
    │   │   │   ├── input/
    │   │   │   │   ├── rest/      (controllers + request/response DTOs + validation)
    │   │   │   │   ├── scheduler/ (statistics/reconciliation & data-absence watchdog)
    │   │   │   │   └── binance/   (KlineWebSocketListener → ProcessClosedCandleUseCase,
    │   │   │   │                    reconnect logic)
    │   │   │   └── output/
    │   │   │       ├── persistence/ (JPA entities, repositories, mappers)
    │   │   │       ├── binance/     (Binance WebSocket/REST client, DTOs, mappers)
    │   │   │       └── telegram/    (TelegramNotificationAdapter, DTOs)
    │   │   └── configuration/    (bean wiring, WebClient/WebSocket beans, profiles,
    │   │                          scheduler config, TradingProperties)
    │   └── resources/
    │       ├── application.yml          (defaults: TRADING_MODE=PAPER, MAX_OPEN_TRADES=1)
    │       ├── application-local.yml
    │       ├── application-test.yml
    │       ├── application-docker.yml
    │       └── db/migration/            (Flyway V1..V6)
    └── test/
        └── kotlin/com/example/tradingbot/
            ├── domain/                  (pure unit tests)
            ├── application/             (use-case tests with fakes)
            ├── adapter/                 (integration tests: persistence, telegram, binance)
            └── architecture/            (ArchUnit rules)
```

**Structure Decision**: Modular monolith with the required hexagonal layout. Single Gradle module
with strict package boundaries verified by ArchUnit; this keeps v1 simple (no microservices, no
multi-module overhead) while enforcing the constitution's dependency direction mechanically.

## Architecture Decisions

1. **Version stack**: Spring Boot 4.1.x, Kotlin 2.3.x, Java 21 LTS, Gradle 9.3 (rationale in
   research.md §1). Versions pinned in `gradle/libs.versions.toml`.
2. **Dependency rule**: `domain` depends on nothing; `application` depends on `domain` only;
   `adapter.*` depends on `application` ports (+ frameworks); `configuration` wires
   implementations. Enforced by ArchUnit.
3. **Single global lock row**: `trading_state` (1 row) locked with `SELECT ... FOR UPDATE` inside
   the open/close transaction; unique generated-column guard as DB backstop.
4. **Idempotency**: unique key on signals `(symbol, timeframe, candle_open_time, strategy)`;
   insert-on-conflict returns the existing row; trade creation guarded by the state lock.
5. **Financial formulas** (BigDecimal, scale 8, HALF_UP; no `Double`):
   - `entryNotional = entryPrice × quantity`
   - `exitNotional = exitPrice × quantity`
   - `grossPnl = (exitPrice − entryPrice) × quantity`
   - `fees = entryNotional × feePct + exitNotional × feePct`
   - `slippageCost = (entryNotional + exitNotional) × slippagePct`
   - `netPnl = grossPnl − fees − slippageCost`
   - `returnPct = netPnl / entryNotional × 100` (scale 4)
   - `result = WIN if netPnl > 0; LOSS if < 0; else BREAK_EVEN`
   - `quantity = configuredNotional / entryPrice` (rounded down to symbol step where known)
   All results are persisted at close; never recalculated from live prices later.
6. **Notification model**: internal `NotificationMessage` value objects (BUY, SELL, ignored,
   opened, closed, critical error, market-data loss, emergency, periodic summary); adapter maps
   to Telegram `sendMessage`; messages always include `PAPER TRADING`.
7. **Time policy**: all persistence and computations in UTC `Instant`; DB columns `TIMESTAMP(6)`.

## Data Flow

```text
Binance WS (kline stream) ──► BinanceKlineListener (input adapter)
      │  parse + validate + dedupe (closed candle only)
      ▼
ProcessClosedCandleUseCase ──► GenerateSignalUseCase ──► TradingStrategy.evaluate(candle, ctx)
      │                                                       │
      │                                        BUY/SELL/HOLD signal (persisted, event emitted)
      │                                                       ▼
      └──────────────────────────── OpenPaperTradeUseCase / ClosePaperTradeUseCase
                                         │  (transaction: lock trading_state row,
                                         │   check open trade, create/close trade,
                                         │   persist result, update state)
                                         ▼
                                  NotificationPort ──► TelegramNotificationAdapter
                                         │
       REST controllers ──► use cases ────┘   (stats from StatisticsRepositoryPort)
```

## Use Cases (input ports)

| Port | Responsibility |
|---|---|
| `ProcessClosedCandleUseCase` | Entry for a closed candle from the Binance listener |
| `GenerateSignalUseCase` | Run strategy on a candle, persist signal, reject duplicates |
| `OpenPaperTradeUseCase` | Validate + create the single open trade (transactional) |
| `ClosePaperTradeUseCase` | Close by SELL/SL/TP/manual/emergency/expiration (transactional) |
| `EvaluateOpenTradeUseCase` | Check open trade against current price for SL/TP/expiration |
| `GetOpenTradeUseCase` | Read current open trade |
| `GetTradingStatisticsUseCase` | Aggregate results with filters |
| `EnableSignalProcessingUseCase` / `DisableSignalProcessingUseCase` | Kill-switch with actor/time audit |
| `GetSystemStatusUseCase` | Status snapshot for `/status` |

## Output Ports

| Port | Capability |
|---|---|
| `MarketDataPort` | Provide current market price / kline for a symbol (used by close evaluation) |
| `SignalRepositoryPort` | Persist/find signals; dedupe by candle key |
| `PaperTradeRepositoryPort` | Open/find/close trades; query history |
| `TradingStatePort` | Read/update global state; expose lock scope (transactional) |
| `NotificationPort` | Send typed notification messages (Telegram adapter in prod) |
| `StatisticsRepositoryPort` | Query aggregated statistics |
| `ClockPort` | Time source (deterministic in tests) |
| `TransactionPort` | Delimit atomic open/close operations (thin adapter over Spring `@Transactional`) |

## Adapters

- **Input — Binance**: `KlineWebSocketListener` subscribes to configured
  `wss://stream.binance.com:9443/ws/<sym>@kline_<tf>`, filters `k.x == true`, maps to
  `MarketCandle`, calls `ProcessClosedCandleUseCase`. Handles reconnect (backoff) and data-absence
  watchdog. Never uses private credentials; never sends orders.
- **Input — REST**: controllers per `contracts/api.md` (query + safe control only), validation via
  Bean Validation, DTO mapping, no business logic.
- **Input — Scheduler**: periodic `EvaluateOpenTradeUseCase` (SL/TP/expiration) and stats/health
  refresh; data-absence watchdog.
- **Output — Persistence**: JPA entities + Spring Data repos (inside the adapter only), explicit
  mappers, `SELECT ... FOR UPDATE` on the state row.
- **Output — Binance**: WebSocket/REST client for public data (REST fallback/history loader only).
- **Output — Telegram**: `WebClient`-based `sendMessage`, env token/chatId, timeout + retries,
  fire-and-forget error handling, no token in logs.

## Persistence Strategy

Full relational model, Flyway V1–V6, mappers, indexes, idempotency, and mapping table in
[data-model.md](data-model.md). Summary: `signals`, `paper_trades` (+ `open_guard` unique
generated column), `trading_state` (single lock row), optional `trade_statistics_daily`;
audit columns everywhere; no `ddl-auto=update`.

## Concurrency Strategy

- Open/close flows run in one `@Transactional` method that first executes
  `SELECT ... FOR UPDATE` on `trading_state` (id = 1), then validates `open_trade_id`, performs
  the write, and updates state — all-or-nothing.
- DB backstop: `UNIQUE (open_guard)` on `paper_trades` rejects a second OPEN row.
- Concurrent test: N simultaneous BUY signals ⇒ exactly 0 or 1 OPEN trades; the rest are
  `IGNORED` with `GLOBAL_TRADE_ALREADY_OPEN` (verified with an ExecutorService firing two
  transactions in parallel against Testcontainers MySQL).

## Testing Strategy

- **Unit (domain)**: value objects, `TradeResultCalculator` (positive/negative/breakeven,
  rounding), `SignalEvaluator`, single-open-trade rule with fakes, SL/TP, idempotency, invalid
  states. No Spring.
- **Integration**: Testcontainers MySQL for Flyway migrations, state locking, restart recovery,
  open-trade reconciliation; MockWebServer for Telegram adapter (timeout/retry/error, no token
  leak); simulated WebSocket server for the Binance adapter (closed-candle filter, reconnect).
- **Architecture (ArchUnit)**: domain free of Spring/JPA/infrastructure; adapters implement ports;
  no dependency from domain → infrastructure; controllers have no business logic; JPA entities
  stay in the persistence adapter package; package conventions.
- **Concurrency**: two parallel BUY ⇒ ≤ 1 OPEN (Testcontainers).

## Observability

- Structured JSON logs (Logback encoder) with MDC fields: `correlationId`, `signalId`, `tradeId`.
- Micrometer metrics: `candles.processed`, `signals.generated`/`.ignored` (by reason),
  `trades.opened`/`.closed`, `trades.net_pnl`, `errors.<adapter>` (binance/mysql/websocket/telegram).
- Actuator health indicators: MySQL, Binance (last candle age / stream connected),
  Telegram (config presence + last send status); `/api/v1/health` aggregates.
- Data-absence detection: watchdog sets `market_data_healthy=false` after a configurable window
  without closed candles; exposed in `/status`, metrics, and Telegram alert.
- Retry/circuit-breaker policy: Resilience4j for HTTP calls (Telegram, Binance REST); WebSocket
  reconnect loop with exponential backoff + jitter. Logs never include tokens or secrets.

## Technical Risks

| Risk | Mitigation |
|---|---|
| MySQL 8 lock semantics differ across isolation levels | Default `REPEATABLE_READ` is fine with `SELECT ... FOR UPDATE`; verified by Testcontainers concurrency test |
| Binance stream drops / duplicate delivery | Reconnect loop + unique candle key + insert-on-conflict dedupe |
| Telegram rate limits / outages | Configurable timeout, bounded retries, fire-and-forget, never blocks engine |
| JPA + Kotlin pitfalls (`data class` entities) | `kotlin-jpa` no-arg plugin; regular classes with id-based equality; verified in ArchUnit |
| Financial precision errors | `BigDecimal` everywhere; fixed scale/rounding; formula unit tests; no `Double` |
| Spring Boot 4 / Kotlin 2.3 migration quirks | Versions pinned & researched (research.md §1); KGP 2.3.21 + Gradle 9.3 compatible |
| Architecture drift | ArchUnit enforced in CI (`./gradlew test` includes architecture tests) |

## Pending Decisions

- **Manual-close authorization**: v1 uses a simple `actor` field in the request body; token-based
  admin auth is deferred (documented in contracts).
- **Statistics materialization**: computed on demand in v1; `trade_statistics_daily` table is
  designed but deferred.
- **Initial strategy parameters**: default SMA + RSI parameter set TBD; configurable via
  `TRADING_*` env vars, isolated behind `TradingStrategy` interface.
- **REST fallback for historical data**: isolated in the Binance adapter; decide in v1 whether
  history loading is needed (not required for live signals).
- **Periodic result summary cadence**: default daily at 00:00 UTC; configurable.

## Recommended Implementation Order

1. Gradle scaffold: version catalog, Spring Boot 4.1 + Kotlin 2.3, profiles, `TradingBotApplication`.
2. Domain core: value objects, models, enums, exceptions, `TradeResultCalculator`, pure unit tests.
3. Application layer: ports (input/output) + use-case services with fakes; domain unit tests for
   open/close/single-trade rules.
4. Persistence adapter: entities, mappers, Flyway V1–V6, repositories, state locking; Testcontainers
   integration + concurrency tests.
5. Binance input adapter: kline listener, closed-candle filter, reconnect, watchdog; simulated
   WebSocket tests.
6. Strategy (initial SMA+RSI) behind `TradingStrategy`; unit tests.
7. Telegram output adapter: `NotificationPort` + WebClient adapter; MockWebServer tests.
8. REST/scheduler input adapters + `configuration` wiring; contract tests.
9. Observability: actuator indicators, metrics, structured logs; health tests.
10. Docker Compose + Dockerfile + `.env.example`; end-to-end quickstart scenarios.
11. ArchUnit architecture tests as a gating suite; final full `./gradlew test` pass.

## Complexity Tracking

> Not applicable — no constitution violations. The table is intentionally empty.
