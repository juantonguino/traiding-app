---

description: "Task list for Trading Signal Backend implementation"

---

# Tasks: Trading Signal Backend

**Input**: Design documents from `/specs/001-trading-signal-backend/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: The feature specification explicitly requests unit, integration, architecture, and concurrency tests — test tasks are included and MUST be written first (fail before implementation).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1..US8 from spec.md)
- Include exact file paths in descriptions

## Path Conventions

- Base Kotlin package: `src/main/kotlin/com/example/tradingbot/`
- Base test package: `src/test/kotlin/com/example/tradingbot/`
- Config/resources: `src/main/resources/`
- Root: `build.gradle.kts`, `settings.gradle.kts`, `Dockerfile`, `docker-compose.yml`, `.env.example`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and build/config scaffolding

- [X] T001 Create Gradle Kotlin DSL project: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/` — Spring Boot 4.1.x, Kotlin 2.3.x, Java 21 LTS, Gradle 9.3, plugins `kotlin-spring` + `kotlin-jpa`, dependency management
- [X] T002 [P] Create application entry point `TradingBotApplication.kt` in `src/main/kotlin/com/example/tradingbot/`
- [X] T003 [P] Create `TradingProperties` config class and `application.yml` with safe defaults: `TRADING_MODE=PAPER`, `MAX_OPEN_TRADES=1`, `ALLOW_REAL_ORDERS=false` in `src/main/kotlin/com/example/tradingbot/configuration/` and `src/main/resources/application.yml`
- [X] T004 [P] Create profiles `application-local.yml`, `application-test.yml`, `application-docker.yml` in `src/main/resources/`
- [X] T005 [P] Create `.env.example` documenting `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`, `TRADING_SYMBOLS`, `TRADING_TIMEFRAME`, `TRADING_STRATEGY`, fee/slippage percentages, SL/TP defaults, timeouts, retries, and data-absence threshold (real `.env` stays gitignored)
- [X] T006 [P] Set up structured JSON logging (Logback encoder) + MDC filter for `correlationId`, `signalId`, `tradeId` in `src/main/resources/logback-spring.xml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domain core, ports, persistence, and architecture verification that MUST be complete before ANY user story

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T007 Create value objects in `src/main/kotlin/com/example/tradingbot/domain/valueobject/` (`Symbol`, `Price`, `Quantity`, `Percentage`, `Money`, `Timeframe`, `TradeId`, `SignalId`, `Commission`, `Slippage`) — all monetary values `BigDecimal`
- [X] T008 Create domain models and enums in `src/main/kotlin/com/example/tradingbot/domain/model/` (`MarketCandle`, `TradingSignal`, `SignalSide`, `SignalStatus`, `PaperTrade`, `TradeStatus`, `TradeResult`, `CloseReason`, `TradingState`, `TradingStatistics`) — plain Kotlin, no annotations
- [X] T009 [P] Create domain exceptions in `src/main/kotlin/com/example/tradingbot/domain/exception/` (`OpenTradeExistsException`, `NoOpenTradeException`, `SignalsDisabledException`, `EmergencyActiveException`, `DuplicateCandleException`, `InvalidDataException`)
- [X] T010 Implement `TradeResultCalculator` in `src/main/kotlin/com/example/tradingbot/domain/service/` using the fixed formulas (entryNotional, grossPnl, fees, slippageCost, netPnl, returnPct, result) with scale 8 / HALF_UP, no `Double`
- [X] T011 [P] Create domain events in `src/main/kotlin/com/example/tradingbot/domain/event/` (`SignalGeneratedEvent`, `TradeOpenedEvent`, `TradeClosedEvent`, `SignalIgnoredEvent`)
- [X] T012 Define input ports in `src/main/kotlin/com/example/tradingbot/application/port/input/` (`ProcessClosedCandleUseCase`, `GenerateSignalUseCase`, `OpenPaperTradeUseCase`, `ClosePaperTradeUseCase`, `EvaluateOpenTradeUseCase`, `GetOpenTradeUseCase`, `GetTradingStatisticsUseCase`, `EnableSignalProcessingUseCase`, `DisableSignalProcessingUseCase`, `GetSystemStatusUseCase`)
- [X] T013 Define output ports in `src/main/kotlin/com/example/tradingbot/application/port/output/` (`MarketDataPort`, `SignalRepositoryPort`, `PaperTradeRepositoryPort`, `TradingStatePort`, `NotificationPort`, `StatisticsRepositoryPort`, `ClockPort`, `TransactionPort`)
- [X] T014 Implement `SystemClockPort` (wrapping `Clock.systemUTC()`) and `SpringTransactionPort` (thin adapter over `@Transactional`) in `src/main/kotlin/com/example/tradingbot/configuration/`
- [X] T015 Create persistence adapter: JPA entities, mappers, and Spring Data repositories for `signals`, `paper_trades` (with `open_guard` generated column), `trading_state` in `src/main/kotlin/com/example/tradingbot/adapter/output/persistence/`
- [X] T016 Create Flyway migrations `V1__create_signals.sql`, `V2__create_paper_trades.sql`, `V3__create_trading_state.sql`, `V4__seed_trading_state.sql`, `V5__create_trade_statistics_daily.sql`, `V6__add_indexes.sql` in `src/main/resources/db/migration/` per data-model.md (no `ddl-auto=update`)
- [X] T017 [P] Implement ArchUnit architecture tests in `src/test/kotlin/com/example/tradingbot/architecture/` (domain free of Spring/JPA/infrastructure, adapters implement ports, controllers no business rules, JPA entities stay in persistence adapter)
- [X] T018 Implement application services (all use cases) as thin orchestration in `src/main/kotlin/com/example/tradingbot/application/service/` with constructor-injected ports (framework-free)
- [X] T019 [P] Create `Dockerfile` (multi-stage: gradle build → `eclipse-temurin:21-jre`) and `docker-compose.yml` (app + `mysql:8.4`, healthcheck, persistent volume, internal network, minimal ports) at repository root
- [X] T020 Write unit tests for `TradeResultCalculator` (positive, negative, breakeven, rounding, `Double` absence) in `src/test/kotlin/com/example/tradingbot/domain/service/TradeResultCalculatorTest.kt`

**Checkpoint**: Foundation ready — domain is framework-free, ports defined, persistence + migrations working, architecture tests green. User story implementation can now begin.

---

## Phase 3: User Story 1 - Receive a buy signal (Priority: P1) 🎯 MVP

**Goal**: Consume public Binance Spot klines, process only closed candles, generate and persist BUY/SELL/HOLD signals with full data, and emit a signal event.

**Independent Test**: Start the app with a configured symbol; a closed candle from the (simulated or live) Binance stream produces exactly one recorded signal with symbol/price/interval/strategy/timestamp.

### Tests for User Story 1 ⚠️

- [X] T021 [P] [US1] Integration test for Binance adapter with a simulated WebSocket server (closed-candle filter `k.x==true`, in-progress candle ignored, duplicate candle not reprocessed) in `src/test/kotlin/com/example/tradingbot/adapter/input/binance/KlineListenerIntegrationTest.kt`
- [X] T022 [P] [US1] Unit test for signal generation (closed candle → BUY signal with all required fields; duplicate candle → no second signal) in `src/test/kotlin/com/example/tradingbot/application/service/GenerateSignalUseCaseTest.kt`

### Implementation for User Story 1

- [X] T023 [P] [US1] Create Binance kline WebSocket DTOs and mapper (external → `MarketCandle`, using `k.x` close flag and `symbol/timeframe/k.t` identity) in `src/main/kotlin/com/example/tradingbot/adapter/input/binance/`
- [X] T024 [US1] Implement `KlineWebSocketListener` that subscribes to configured streams, filters closed candles, dedupes, and calls `ProcessClosedCandleUseCase` in `src/main/kotlin/com/example/tradingbot/adapter/input/binance/`
- [X] T025 [US1] Implement `GenerateSignalUseCase` in `src/main/kotlin/com/example/tradingbot/application/service/` (run strategy, persist signal via `SignalRepositoryPort`, emit `SignalGeneratedEvent`)
- [X] T026 [US1] Implement `SignalRepositoryPort` + JPA implementation with idempotent insert (unique on `symbol, timeframe, candle_open_time, strategy`) in `src/main/kotlin/com/example/tradingbot/adapter/output/persistence/`
- [X] T027 [US1] Implement initial replaceable `TradingStrategy` (SMA + RSI) + `StrategyContext` in `src/main/kotlin/com/example/tradingbot/application/service/` (returns `TradingSignal?` with reason/confidence, no external deps)
- [X] T028 [US1] Add reconnect handling (exponential backoff + jitter) and data-absence watchdog hook to the Binance listener

**Checkpoint**: User Story 1 fully functional and independently testable — a closed candle yields one recorded, notified-capable signal.

---

## Phase 4: User Story 2 - Open a simulated trade (Priority: P1)

**Goal**: A valid BUY signal opens a simulated trade (state OPEN) with full entry data, originating signal, and opening event.

**Independent Test**: Process a valid BUY when no trade is open; verify one OPEN trade with symbol, entry price, quantity, timestamps, strategy, and originating signal.

### Tests for User Story 2 ⚠️

- [X] T029 [P] [US2] Integration test for open-trade persistence + state lock (Testcontainers MySQL) in `src/test/kotlin/com/example/tradingbot/adapter/output/persistence/OpenTradeIntegrationTest.kt`
- [X] T030 [P] [US2] Unit test for `OpenPaperTradeUseCase` (no open trade → opens; open trade exists → throws/rejects) in `src/test/kotlin/com/example/tradingbot/application/service/OpenPaperTradeUseCaseTest.kt`

### Implementation for User Story 2

- [X] T031 [US2] Implement `OpenPaperTradeUseCase` in `src/main/kotlin/com/example/tradingbot/application/service/` (inside `TransactionPort`: lock `trading_state` row, verify none OPEN, compute quantity from configured notional, create trade, persist, update state, emit `TradeOpenedEvent`)
- [X] T032 [US2] Implement `PaperTradeRepositoryPort` + JPA implementation in `src/main/kotlin/com/example/tradingbot/adapter/output/persistence/`
- [X] T033 [US2] Implement `TradingStatePort` + JPA implementation with `SELECT ... FOR UPDATE` on the single state row in `src/main/kotlin/com/example/tradingbot/adapter/output/persistence/`
- [X] T034 [US2] Wire `OpenPaperTradeUseCase` into the signal flow (accepted BUY → open trade)

**Checkpoint**: User Story 2 works — BUY opens a single OPEN trade with full data.

---

## Phase 5: User Story 3 - Prevent simultaneous trades (Priority: P1)

**Goal**: Enforce the global single-open-trade rule across all symbols, rejecting new BUYs with an audited IGNORED state and reason, safe under concurrency.

**Independent Test**: With a trade OPEN on BTCUSDT, a BUY for ETHUSDT is recorded as IGNORED with `GLOBAL_TRADE_ALREADY_OPEN`; after close, a new BUY is accepted. Two simultaneous BUYs produce at most one OPEN.

### Tests for User Story 3 ⚠️

- [X] T035 [P] [US3] Concurrency test: N parallel BUY signals → exactly 0 or 1 OPEN trade, rest IGNORED (Testcontainers MySQL, ExecutorService) in `src/test/kotlin/com/example/tradingbot/adapter/output/persistence/SingleOpenTradeConcurrencyTest.kt`
- [X] T036 [P] [US3] Unit test for the ignore rule (cross-symbol rejection, ignore reason recorded, re-entry after close) in `src/test/kotlin/com/example/tradingbot/application/service/SingleOpenTradeRuleTest.kt`

### Implementation for User Story 3

- [X] T037 [US3] Implement the ignore path in the signal flow (rejected BUY → signal status `IGNORED` + `ignoreReason=GLOBAL_TRADE_ALREADY_OPEN` + `SignalIgnoredEvent`) in `src/main/kotlin/com/example/tradingbot/application/service/`
- [X] T038 [US3] Ensure the `open_guard` unique generated column is enforced in the persistence mapping per data-model.md (DB backstop)
- [X] T039 [US3] Add `OpenTradeExistsException` handling to notifications flow (include open trade symbol)

**Checkpoint**: User Story 3 verified — single open trade guaranteed, including under concurrency.

---

## Phase 6: User Story 4 - Close a simulated trade (Priority: P1)

**Goal**: Close the open trade via SELL signal, stop-loss, take-profit, manual close, or emergency, computing and persisting the full financial result.

**Independent Test**: Trigger each close condition and verify exit price/time, close reason, duration, gross/net PnL, costs, return %, and WIN/LOSS/BREAK_EVEN are recorded and a close event is emitted.

### Tests for User Story 4 ⚠️

- [X] T040 [P] [US4] Integration test for close flow (SELL, SL, TP, manual, emergency → persisted result) with Testcontainers in `src/test/kotlin/com/example/tradingbot/adapter/output/persistence/CloseTradeIntegrationTest.kt`
- [X] T041 [P] [US4] Unit test for `TradeResultCalculator`-backed close computations and result classification in `src/test/kotlin/com/example/tradingbot/application/service/ClosePaperTradeUseCaseTest.kt`

### Implementation for User Story 4

- [X] T042 [US4] Implement `ClosePaperTradeUseCase` in `src/main/kotlin/com/example/tradingbot/application/service/` (inside `TransactionPort`: lock state row, verify OPEN trade, compute exit price + result via `TradeResultCalculator`, persist close, release state, emit `TradeClosedEvent`)
- [X] T043 [US4] Implement `EvaluateOpenTradeUseCase` (evaluate SL/TP/expiration against current price) in `src/main/kotlin/com/example/tradingbot/application/service/`
- [X] T044 [US4] Implement `MarketDataPort` + Binance price/kline adapter for close evaluation in `src/main/kotlin/com/example/tradingbot/adapter/output/binance/`
- [X] T045 [US4] Wire SELL-signal closes and scheduled evaluation (`EvaluateOpenTradeUseCase`) via the scheduler adapter in `src/main/kotlin/com/example/tradingbot/adapter/input/scheduler/`

**Checkpoint**: User Story 4 verified — each close reason produces a complete, audited result.

---

## Phase 7: User Story 7 - Operate in safe mode (Priority: P1)

**Goal**: Guarantee PAPER TRADING-only operation with a global kill switch, safe control endpoints, and startup rejection of unsafe configuration.

**Independent Test**: Start with `TRADING_MODE=PAPER`/`MAX_OPEN_TRADES=1`/`ALLOW_REAL_ORDERS=false`; verify `/status` shows mode; disabling signals blocks new trades while the open trade keeps being monitored; unsafe config refuses to start.

### Tests for User Story 7 ⚠️

- [X] T046 [P] [US7] Integration test for control endpoints (disable records actor/time, enable restores, emergency-stop closes open trade) in `src/test/kotlin/com/example/tradingbot/adapter/input/rest/ControlControllerIntegrationTest.kt`
- [X] T047 [P] [US7] Unit test for startup config validation (rejects unsafe/inconsistent values) in `src/test/kotlin/com/example/tradingbot/configuration/TradingPropertiesTest.kt`

### Implementation for User Story 7

- [X] T048 [US7] Implement `EnableSignalProcessingUseCase`, `DisableSignalProcessingUseCase`, `GetSystemStatusUseCase` in `src/main/kotlin/com/example/tradingbot/application/service/`
- [X] T049 [US7] Implement REST controllers + DTOs + validation: `GET /health`, `GET /status`, `POST /control/signals/enable`, `POST /control/signals/disable`, `POST /control/emergency-stop`, `POST /trades/open/close` in `src/main/kotlin/com/example/tradingbot/adapter/input/rest/` (per contracts/api.md; never return JPA entities, no business logic)
- [X] T050 [US7] Implement startup validation of `TradingProperties` (reject `TRADING_MODE != PAPER`, `MAX_OPEN_TRADES != 1`, `ALLOW_REAL_ORDERS = true`) in `src/main/kotlin/com/example/tradingbot/configuration/`
- [X] T051 [US7] Implement emergency-stop: close open trade (reason `EMERGENCY`), set `emergency_active`, disable signals

**Checkpoint**: User Story 7 verified — the system cannot execute real orders and is safely controllable.

---

## Phase 8: User Story 6 - Receive Telegram alerts (Priority: P2)

**Goal**: Send PAPER TRADING-labeled Telegram notifications for signals, ignored signals, openings/closings, and system events without ever blocking the engine.

**Independent Test**: Generate each event type with a stub Telegram HTTP server; verify messages arrive with required fields, failures are logged without the token, and analysis continues.

### Tests for User Story 6 ⚠️

- [X] T052 [P] [US6] Integration test for the Telegram adapter with MockWebServer (sendMessage payload, timeout, retry, no-token-in-logs) in `src/test/kotlin/com/example/tradingbot/adapter/output/telegram/TelegramNotificationAdapterIntegrationTest.kt`
- [X] T053 [P] [US6] Unit test for notification message formatting (BUY/SELL/ignored/opened/closed/error/emergency include required fields + PAPER TRADING) in `src/test/kotlin/com/example/tradingbot/application/port/output/NotificationMessageTest.kt`

### Implementation for User Story 6

- [X] T054 [US6] Define `NotificationPort` contract and typed message models (BUY, SELL, ignored, opened, closed, critical error, market-data loss, emergency, summary) in `src/main/kotlin/com/example/tradingbot/application/port/output/`
- [X] T055 [US6] Implement `TelegramNotificationAdapter` using `WebClient` (env token/chatId, configurable timeout, bounded retries, fire-and-forget error handling, token never logged) in `src/main/kotlin/com/example/tradingbot/adapter/output/telegram/`
- [X] T056 [US6] Wire notifications into signal generation, open/close flows, emergency, data-absence detection, and periodic summary

**Checkpoint**: User Story 6 verified — all event types notify with PAPER TRADING label; a Telegram outage never stops analysis.

---

## Phase 9: User Story 5 - Consult gains and losses (Priority: P2)

**Goal**: Query accumulated and per-trade results (gross, costs, net, return %) and filtered statistics.

**Independent Test**: After several closed trades, `/statistics` and `/trades` return aggregated results and per-trade detail that match the recorded history.

### Tests for User Story 5 ⚠️

- [X] T057 [P] [US5] Integration test for statistics aggregation (Testcontainers) in `src/test/kotlin/com/example/tradingbot/adapter/output/persistence/StatisticsIntegrationTest.kt`
- [X] T058 [P] [US5] Contract test for `/trades`, `/trades/{tradeId}`, `/statistics` DTO shapes in `src/test/kotlin/com/example/tradingbot/adapter/input/rest/TradesContractTest.kt`

### Implementation for User Story 5

- [X] T059 [US5] Implement `GetTradingStatisticsUseCase` + `StatisticsRepositoryPort` aggregation (gains, losses, net, win rate, averages, drawdown, by symbol/strategy/timeframe/day/month) in `src/main/kotlin/com/example/tradingbot/application/service/` and `src/main/kotlin/com/example/tradingbot/adapter/output/persistence/`
- [X] T060 [US5] Implement REST endpoints `GET /trades`, `GET /trades/{tradeId}`, `GET /trades/open`, `GET /statistics` with filters in `src/main/kotlin/com/example/tradingbot/adapter/input/rest/`

**Checkpoint**: User Story 5 verified — gains/losses and statistics are queryable.

---

## Phase 10: User Story 8 - Recover the full history (Priority: P2)

**Goal**: Query the complete auditable history — accepted/ignored signals and open/closed trades — with every trade linked to its originating signal.

**Independent Test**: Query `/signals` (incl. IGNORED with reason) and `/trades`; reconstruct each trade's outcome from its originating signal.

### Tests for User Story 8 ⚠️

- [X] T061 [P] [US8] Integration test for history queries + trade↔signal reconstruction (Testcontainers) in `src/test/kotlin/com/example/tradingbot/adapter/input/rest/HistoryContractTest.kt`
- [X] T062 [P] [US8] Unit test for audit reconstruction (open/close signal references preserved) in `src/test/kotlin/com/example/tradingbot/application/service/HistoryAuditTest.kt`

### Implementation for User Story 8

- [X] T063 [US8] Implement `GET /signals` endpoint with filters (symbol/status/side/period) in `src/main/kotlin/com/example/tradingbot/adapter/input/rest/`
- [X] T064 [US8] Ensure trades persist `open_signal_id` and `close_signal_id` references and expose them in responses per data-model.md / contracts
- [X] T065 [US8] Add query support for ignored signals with `ignoreReason` and open-trade context

**Checkpoint**: User Story 8 verified — full history is queryable and decisions are reconstructable.

---

## Phase 11: Polish & Cross-Cutting Concerns

**Purpose**: Observability, health, resilience, and release validation across all stories

- [X] T066 [P] Implement Actuator health indicators (MySQL, Binance stream age, Telegram) in `src/main/kotlin/com/example/tradingbot/configuration/`
- [X] T067 [P] Add Micrometer metrics: candles processed, signals by status, ignored by reason, trades opened/closed, net PnL, errors by adapter in `src/main/kotlin/com/example/tradingbot/adapter/`
- [X] T068 [P] Implement the data-absence watchdog scheduler (sets `market_data_healthy=false`, alerts after configurable window) in `src/main/kotlin/com/example/tradingbot/adapter/input/scheduler/`
- [X] T069 [P] Add Resilience4j retry/circuit-breaker config for Telegram and Binance REST HTTP calls in `src/main/kotlin/com/example/tradingbot/configuration/`
- [X] T070 [P] Implement the periodic result summary scheduler (default daily 00:00 UTC) in `src/main/kotlin/com/example/tradingbot/adapter/input/scheduler/`
- [X] T071 [P] Run all `quickstart.md` validation scenarios end-to-end (health, data reception, signal test, single-trade rule, results, restart recovery)
- [X] T072 [P] Update feature documentation and run the full `./gradlew test` suite as a final gate

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **User Stories (Phase 3+)**: Depend on Foundational completion; then sequential in priority order (P1 first)
- **Polish (Phase 11)**: Depends on all desired user stories being complete

### User Story Dependencies

- **US7 (safe mode)**: after Foundational — configuration + control endpoints; no other story required
- **US1 (P1)**: after Foundational — needs `GenerateSignalUseCase`, `SignalRepositoryPort`, Binance listener, initial strategy
- **US2 (P1)**: depends on US1 (BUY signal flow) — opens trade from accepted BUY
- **US3 (P1)**: depends on US2 (needs OPEN trade state) — enforces the global rule
- **US4 (P1)**: depends on US2 + US3 (needs trade lifecycle + state lock)
- **US6 (P2)**: after Foundational (`NotificationPort`); notifications integrate with US1–US4 events
- **US5 (P2)**: depends on US4 (needs closed-trade results)
- **US8 (P2)**: depends on US1 + US2 + US4 (needs recorded signals and trades)

### Within Each User Story

- Tests MUST be written first and FAIL before implementation
- Models/value objects before services; services before endpoints; implementation before integration

### Parallel Opportunities

- All Phase 1 tasks marked [P] run in parallel
- All Phase 2 tasks marked [P] run in parallel
- T007–T020 foundational domain/ports/persistence tasks can be parallelized where file-disjoint
- Within each story, [P] test tasks run in parallel
- US1, US6, and US7 can start in parallel once Foundational is done (US6/US7 have no cross-story deps for their core); US2–US4 are sequential; US5/US8 wait on results

---

## Parallel Example: User Story 1

```bash
# Launch tests for User Story 1 together (fail-first):
Task: "Integration test for Binance adapter with simulated WebSocket server in src/test/.../KlineListenerIntegrationTest.kt"
Task: "Unit test for signal generation in src/test/.../GenerateSignalUseCaseTest.kt"

# Launch file-disjoint implementation together:
Task: "Binance kline DTOs + mapper in src/main/.../adapter/input/binance/"
Task: "SignalRepositoryPort + JPA implementation in src/main/.../adapter/output/persistence/"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: A closed candle produces exactly one recorded BUY/SELL/HOLD signal
5. Deploy/demo if ready (Docker Compose)

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. US7 (safe mode) → baseline control/health
3. US1 → signals (MVP)
4. US2 → open trade → US3 → single-trade rule → US4 → close + results (core loop)
5. US6 → Telegram alerts (delivery channel)
6. US5 → statistics, US8 → history (evaluation/audit)
7. Polish: observability, health, resilience, final validation

### Parallel Team Strategy

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: US1 (signals)
   - Developer B: US6 (Telegram notifications)
   - Developer C: US7 (safe mode/control)
3. US2–US4 then integrate sequentially (depend on US1)
4. US5/US8 integrate after results exist

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to the spec.md user story for traceability
- Financial math is `BigDecimal` only; no `Double` anywhere in domain/application
- Never use real Binance credentials; never add real-order endpoints
- Verify tests fail before implementing; commit after each task or logical group
- Stop at any checkpoint to validate a story independently
