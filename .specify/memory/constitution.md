<!--
  Sync Impact Report
  -----------------
  Version change: (template/unversioned) → 1.0.0
  Modified principles: N/A — first ratification, all principles newly added
  Added sections:
    - Core Principles: I. Hexagonal Architecture (Ports & Adapters), II. Independent Domain,
      III. Paper Trading Only & Capital Protection, IV. Single Global Open Trade,
      V. Quality & Architectural Verification
    - Section 2: Technical & System Constraints (technology stack, market data, strategy &
      signals, paper trading & results, persistence, Telegram notifications)
    - Section 3: Observability, Deployment & Scope (observability, Docker Compose & deployment,
      initial scope)
    - Governance (constitutional acceptance criteria + versioning policy)
  Removed sections: none
  Deferred TODOs: none — ratification date assumed to be 2026-08-14 (date of ratification).
-->

# Trading Signal Backend Constitution

## Core Principles

### I. Hexagonal Architecture (Ports & Adapters)

The system MUST use hexagonal architecture (Ports & Adapters).

- The domain and use cases MUST remain independent of Spring Boot, JPA, MySQL, Binance, Telegram, Docker and any other external technology.
- Dependencies MUST point inward: adapters may depend on ports, but the domain MUST never depend on concrete adapters.
- The application core MUST NOT import infrastructure classes.
- Port interfaces MUST belong to the application core.
- Adapters MUST implement the ports defined by the application.
- Spring configuration MUST only assemble concrete implementations.
- A service architecture that accesses JPA repositories, HTTP clients, or Binance classes directly without passing through ports MUST NOT be used.

The conceptual structure MUST be (names may adapt, but the separation MUST be preserved):

```text
com.example.tradingbot
├── domain          (model, valueobject, service, event, exception)
├── application     (port/input, port/output, service)
├── adapter
│   ├── input       (rest, scheduler, telegram)
│   └── output      (binance, persistence, telegram)
└── configuration
```

**Input ports** MUST represent application use cases, including: `ProcessMarketCandleUseCase`, `GenerateTradingSignalUseCase`, `OpenPaperTradeUseCase`, `ClosePaperTradeUseCase`, `GetOpenTradeUseCase`, `GetTradingStatisticsUseCase`, `IgnoreSignalUseCase`, `EnableTradingSignalsUseCase`, `DisableTradingSignalsUseCase`.

Input adapters (Binance WebSocket listener, scheduler, REST controller, administrative command, Telegram command) MUST only invoke input ports and MUST NOT contain business rules.

**Output ports** MUST describe the capabilities the application needs from external systems, including: `MarketDataPort`, `SignalRepositoryPort`, `PaperTradeRepositoryPort`, `TradingStatePort`, `NotificationPort`, `ClockPort`, `TransactionPort` (if needed), `StatisticsRepositoryPort`.

Output port interfaces MUST use domain models or application-specific models — never JPA entities, Binance responses, or Telegram types.

**Output adapters** (Binance WebSocket, Binance REST, MySQL/JPA persistence, Telegram, Clock) MUST implement the output ports and translate external models via explicit mappers:

- Binance responses, JPA entities, and Telegram DTOs MUST NOT propagate to the domain.
- Technical errors MUST be converted into appropriate application/domain errors.
- Retries and timeouts MUST belong to the adapter or a technical policy, never to domain entities.

### II. Independent Domain

- Business entities MUST be plain Kotlin classes.
- The domain MUST NOT use Spring annotations, JPA annotations, HTTP DTOs, persistence entities, Binance classes, or Telegram classes.
- The domain MUST NOT depend on WebClient, Reactor, Hibernate, or MySQL.
- Monetary values and prices MUST use `BigDecimal`.
- Dates MUST use appropriate types such as `Instant` or `LocalDateTime` with an explicit temporal policy.
- Business rules MUST be testable without starting Spring or MySQL.
- Replacing an external technology MUST NOT require modifying business rules.

### III. Paper Trading Only & Capital Protection (NON-NEGOTIABLE)

- The system MUST operate exclusively in PAPER TRADING mode.
- The system MUST NOT send real orders to Binance.
- Initial Binance integration MUST use only public data.
- Trading, withdrawal, or transfer permissions MUST NOT be requested or stored.
- Credentials and secrets MUST be managed via environment variables.
- Secrets MUST NEVER appear in source code, logs, HTTP responses, errors, or Telegram messages.
- The system MUST include a global kill switch to disable signal processing.
- On critical errors, invalid data, connection loss, or state inconsistencies, the system MUST stop generating new trades.

### IV. Single Global Open Trade

- The system MUST allow at most one open trade at any time, globally across all symbols and strategies.
- If a trade is open, new BUY signals MUST be rejected.
- Rejected signals MUST be recorded with state `IGNORED` and reason `GLOBAL_TRADE_ALREADY_OPEN`.
- A new BUY trade MUST only open after the previous trade has been correctly closed.
- The rule MUST be protected against race conditions.
- Validation MUST run inside a transaction.
- Integrity MUST be reinforced with a transactional lock or an equivalent MySQL constraint.
- Persisted state MUST be the source of truth for the open trade.

### V. Quality & Architectural Verification (NON-NEGOTIABLE)

- Code MUST follow SOLID principles.
- Every dependency MUST point to a more inner layer.
- Adapters MUST depend on ports, never the reverse.
- Use cases MUST depend on output ports.
- Controllers MUST NOT contain business rules.
- Configuration classes MUST NOT contain business logic.
- Every new feature MUST include tests.
- Tests MUST exist that detect architectural dependency violations.
- The package structure MUST be verifiable automatically.
- Domain classes MUST remain free of framework dependencies.
- Code naming MUST be in English and consistent.
- Functional and technical documentation MUST be kept up to date.

## Technical & System Constraints

**Technology Stack**

- Backend MUST be built with Spring Boot and Kotlin.
- Persistence MUST use MySQL.
- Migrations MUST use Flyway; `ddl-auto=update` MUST NOT be the primary schema evolution mechanism, and migrations MUST be versioned, reproducible, and reviewable.
- All local infrastructure MUST run via Docker Compose.
- HTTP calls MUST preferably use WebClient.
- Tests MUST include unit, contract, and integration tests.
- Domain tests MUST NOT require Spring, MySQL, Binance, or Telegram.
- Persistence tests MUST run against real MySQL via Testcontainers or Docker Compose.
- Configuration MUST be separated using `local`, `test`, and `docker` profiles.

**Market Data**

- The system MUST consume public Binance Spot data.
- Candles MUST be processed only when closed.
- A candle MUST be identified by symbol, interval, and open time.
- Duplicate events MUST NOT produce duplicate signals.
- The WebSocket adapter MUST handle reconnections.
- Connection loss MUST produce logs, metrics, and alerts.
- Price, volume, and timestamp data MUST be validated before entering the core.
- Binance models MUST be transformed into domain/application models inside the adapter.

**Strategy & Signals**

- The strategy MUST be defined via a replaceable interface.
- The strategy MUST receive internal domain data.
- The strategy MUST produce BUY, SELL, or HOLD signals.
- The strategy MUST NOT open or close trades directly, send notifications, access MySQL directly, or depend on Binance.
- Each signal MUST include symbol, interval, strategy, price, confidence, optional stop-loss, optional take-profit, and reason.
- An equivalent signal MUST NOT be generated twice for the same candle.

**Paper Trading & Results**

- The paper trading engine MUST open and close simulated trades.
- Each trade MUST record symbol, strategy, interval, quantity, entry price, exit price, timestamps, and close reason.
- The system MUST compute gross PnL, estimated fees, estimated slippage, net PnL, and return percentage.
- Financial calculations MUST use `BigDecimal`.
- Fees and slippage MUST be configurable.
- Results MUST be reconstructable from recorded data.
- Explicit states MUST be maintained: `OPEN`, `CLOSED`, `IGNORED`.
- Closed trades MUST NOT be modified without preserving audit.
- Statistics MUST be computable by day, symbol, strategy, and interval.

**Persistence**

- MySQL MUST be the primary database.
- JPA entities MUST only exist inside the persistence adapter and MUST map to domain models.
- Spring Data repositories MUST NOT be used directly from the domain.
- The application MUST depend on persistence ports, not Spring Data interfaces.
- Critical operations MUST run inside transactions.
- The system MUST be idempotent under retries.

**Telegram Notifications**

- Telegram MUST be integrated through an output notification port; the domain MUST NOT know Telegram.
- The Telegram adapter MUST translate internal messages to the API-required format.
- The system MUST notify BUY and SELL signals, ignored signals, and simulated trade openings and closings.
- Closings MUST include gross PnL, fees, slippage, and net PnL.
- All messages MUST indicate PAPER TRADING.
- A Telegram failure MUST NOT stop the signal engine.
- Timeouts, retries, and HTTP errors MUST be controlled inside the adapter.

## Observability, Deployment & Scope

**Observability**

- The system MUST use structured logs.
- Each signal and trade MUST have a correlatable identifier.
- Generation, acceptance, rejection, opening, and closing events MUST be logged.
- Metrics MUST exist for processed candles, generated signals, ignored signals, open trades, closed trades, profits, losses, and errors.
- Errors from Binance, MySQL, WebSocket, and Telegram MUST be distinguishable.
- A health check endpoint MUST exist.
- The system MUST detect absence of market data for a configurable period.

**Docker Compose & Deployment**

- The application and MySQL MUST run via Docker Compose.
- The application service MUST wait until MySQL is healthy.
- Secrets MUST come from environment variables or files excluded from version control.
- `.env` MUST NOT be included in Git.
- MySQL MUST use a persistent volume.
- Exposed ports MUST be limited to what is necessary.
- Containers MUST NOT use unnecessary privileges.
- Configuration MUST be reproducible across environments.
- The application image MUST be built via a versioned Dockerfile.

**Initial Scope**

v1 MUST include: public Binance Spot candle consumption; closed-candle processing; symbol and interval configuration; one initial replaceable strategy; BUY, SELL, and HOLD signals; paper trading; at most one open trade globally; signal and ignored-signal logging; per-trade PnL calculation; MySQL persistence; Telegram notifications; Docker Compose; Flyway; automated tests; a basic health endpoint; automatic hexagonal architecture verification.

v1 MUST NOT include: real order execution, futures, margin trading, leverage, withdrawals, automatic management of private Binance credentials, multiple open trades, automatic position scaling, or real-money management.

## Governance

The constitution supersedes all other practices. Every implementation MUST satisfy the constitutional acceptance criteria below; a PR or review that does not verify compliance MUST NOT be approved. Complexity MUST be justified; simpler designs that still satisfy the constitution are preferred. Amendments MUST be documented, approved, and versioned according to the versioning policy, with a migration plan when behavior changes.

**Constitutional Acceptance Criteria**

An implementation is only considered valid if:

- The domain can be tested without starting Spring.
- Use cases depend on ports, not concrete adapters.
- MySQL can be replaced with another persistence adapter without modifying the domain.
- Binance can be replaced with a simulated provider without modifying the use cases.
- Telegram can be replaced with a test notifier without modifying the domain.
- The single-open-trade rule is protected against concurrency.
- All relevant signals and trades are audited.
- The application remains PAPER TRADING and never executes real orders.
- Automated tests verify business rules and architectural dependencies.

**Versioning Policy**

- MAJOR: backward-incompatible governance/principle removals or redefinitions.
- MINOR: new principle/section added or materially expanded guidance.
- PATCH: clarifications, wording, typo fixes, non-semantic refinements.
- Every amendment MUST update the version line and the Sync Impact Report comment.

**Version**: 1.0.0 | **Ratified**: 2026-08-14 | **Last Amended**: 2026-08-14
