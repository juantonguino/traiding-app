# Research: Trading Signal Backend

**Branch**: `001-trading-signal-backend` | **Date**: 2026-08-14 | **Spec**: [spec.md](spec.md)

Research notes for the decisions made in [plan.md](plan.md). Each entry follows the format
Decision / Rationale / Alternatives considered.

## 1. Version Compatibility (Java, Kotlin, Spring Boot, Gradle)

- **Decision**: Spring Boot **4.1.x** (e.g. 4.1.0) + Kotlin **2.3.x** (e.g. 2.3.21) + Java **21 (LTS)** + Gradle **9.3** (Kotlin DSL).
- **Rationale**: As of July 2026 Spring Boot 3.5 is end-of-life (OSS support ended 2026-06-30) and Spring Boot 4.0/4.1 are the only OSS-supported branches. 4.1 is the recommended target for new projects, is built on Spring Framework 7.0.8, and raises the Kotlin baseline to 2.3 with Kotlin Serialization 1.11. Spring Boot 4.1 explicitly supports Gradle 8.14+ and 9.x. KGP 2.3.21 is fully supported by Gradle up to 9.3.0. Java 21 is a mature LTS with the broadest tooling/Docker ecosystem support and is fully compatible with Spring Boot 4.1 (which supports Java 17–26).
- **Alternatives considered**:
  - Java 25 (LTS): compatible and first-class in Boot 4.1, but newer; Java 21 keeps the widest tooling compatibility for the whole v1.
  - Kotlin 2.4.x: latest, but Spring Boot 4.1's tested baseline is 2.3; 2.4 is riskier for auto-config/compiler-plugin alignment.
  - Spring Boot 4.0.x: supported but 4.1 is current stable; no reason to start a new project on a previous minor.

## 2. WebSocket Client for Binance

- **Decision**: Use Spring's `WebSocketClient` (`StandardWebSocketClient` from `spring-websocket`) for the Binance kline stream, wrapped in a small reconnectable client.
- **Rationale**: Binance public spot streams are plain WebSocket JSON (`wss://stream.binance.com:9443/ws/<symbol>@kline_<interval>`, or the combined `/<stream>/<stream>` form). We need no STOMP/STOMP messaging features; a raw WebSocket client subscribed per (symbol, interval) is the simplest correct fit. Reconnection (with exponential backoff and jitter) and a "data absence" watchdog are implemented once in the adapter and are easy to test with a simulated WebSocket server.
- **Alternatives considered**:
  - Spring WebFlux reactive `WebSocketClient`: heavier; we would pull the whole reactive stack for a fundamentally sequential per-candle flow.
  - OkHttp WebSocket: fine, but adds a dependency outside the Spring ecosystem; Spring's client suffices.
  - Binance Java SDK: unnecessary; introduces vendor coupling and private-API surface we must not use.

## 3. Closed-Candle Processing (Binance kline)

- **Decision**: Subscribe to the Binance kline stream and process only events where `k.x == true` (the "kline closed" flag). Identify candles by `symbol + interval + k.t` (open time). Deduplicate with a unique DB constraint and an in-memory last-processed guard.
- **Rationale**: Binance's kline payload is sent on each tick and once more with `x=true` when the candle closes; using that flag avoids computing signals on live-changing prices, matching the constitution rule "candles processed only when closed". The unique constraint on (symbol, interval, open time, strategy) plus the `SELECT ... FOR UPDATE`-protected global state guarantee idempotency even on redelivery.
- **Alternatives considered**:
  - REST `api/v3/klines` polling of the last closed candle: simpler but adds latency, misses the live close tick, and needs its own ordering/dedup; reserved as a fallback/history loader only.

## 4. Reconnection & Data-Absence Detection

- **Decision**: Implement an explicit reconnect loop with backoff in the Binance adapter, plus a scheduled watchdog that marks `market_data_healthy = false` when no closed candle has been processed within a configurable window, exposing the state through the status endpoint and metrics.
- **Rationale**: WebSocket connections drop; the requirement says reconnection must be handled and absence must be detected and alerted. Keeping both concerns inside the adapter preserves the hexagonal rule that infrastructure concerns never reach the domain.
- **Alternatives considered**:
  - Relying on Spring Retry/Resilience4j circuit breaker alone: good for HTTP calls but a WebSocket stream is a long-lived connection, not a discrete request; a custom loop is clearer. Resilience4j remains for HTTP calls (Binance REST, Telegram).

## 5. MySQL Concurrency for the Single-Open-Trade Rule

- **Decision**: Pessimistic locking on a single `trading_state` row via `SELECT ... FOR UPDATE` inside the same transaction that creates the trade; plus a unique guard column that rejects a second OPEN row.
- **Rationale**: All open/close flows serialize on one global row, making the "at most one open trade" rule atomic across symbols and strategies. The `FOR UPDATE` lock serializes concurrent BUY signals; the unique guard is a final DB-level backstop. MySQL 8 is used (8.4 LTS image), and Flyway manages schema (no `ddl-auto=update`).
- **Alternatives considered**:
  - Optimistic locking with `version`: less blocking but allows a retry storm and does not, by itself, guarantee a single open trade; the pessimistic row is simpler to reason about and matches the constitution's preferred design.
  - A DB `CHECK` on partial uniqueness: MySQL does not support partial indexes; a generated column (`open_guard = IF(status='OPEN', symbol, NULL)`) with a unique index emulates it.

## 6. Telegram Integration

- **Decision**: `NotificationPort` in the application core; a `TelegramNotificationAdapter` (output) using `WebClient` calling `https://api.telegram.org/bot<token>/sendMessage` with a configurable timeout, limited retries, and non-blocking error handling. Token and chat ID come from environment variables only.
- **Rationale**: Keep Telegram out of the domain (constitution). Fire-and-forget with `subscribe`/async + `onErrorResume` ensures a Telegram failure never blocks or stops the signal engine. Errors are logged without the token. `WebClient` is the preferred HTTP client per the constitution.
- **Alternatives considered**:
  - telegrambots library: heavy, pulls its own long-polling machinery and vendor model; a plain WebClient call is sufficient for `sendMessage`.

## 7. WebClient usage

- **Decision**: Use `WebClient` for both Telegram and any Binance REST calls, defined once in `configuration` and injected into adapters.
- **Rationale**: The constitution mandates WebClient as the preferred HTTP client. For a blocking Spring MVC app we still use WebClient (blocking body retrieval) to keep one HTTP style across adapters.
- **Alternatives considered**:
  - `RestClient` (Spring 6.1+): fine, but the constitution explicitly prefers WebClient.

## 8. Testcontainers & Testing

- **Decision**: JUnit 5 + MockK for unit tests; Spring Boot Test + Testcontainers (`mysql:8.4`) for persistence/integration tests; Spring WebSocket simulated server for the Binance adapter; a `MockWebServer`-style HTTP stub (okhttp mockwebserver or WireMock) for Telegram.
- **Rationale**: Persistence tests against real MySQL validate Flyway migrations, locking, and recovery (constitution: persistence tests run against real MySQL via Testcontainers or Docker Compose). Domain tests never start Spring.
- **Alternatives considered**:
  - H2 in MySQL mode: faster but diverges from real MySQL locking semantics; rejected for concurrency-critical tests.

## 9. Hexagonal Architecture Verification

- **Decision**: Add **ArchUnit** (`com.tngtech.archunit:archunit-junit5`) architectural tests that assert the dependency rules (domain → nothing; application → domain only; adapters → ports; configuration → all), and a package/class-naming convention test.
- **Rationale**: The constitution requires automatic detection of architectural dependency violations. ArchUnit is the standard tool, works on Kotlin bytecode, and gives precise failure messages.
- **Alternatives considered**:
  - Manual review: not verifiable automatically (violates constitution).
  - jArchUnit: less mature than ArchUnit.

## 10. Observability

- **Decision**: Spring Boot Actuator for health checks (`/actuator/health` with custom indicators for MySQL, Binance, Telegram) and Micrometer for metrics (candles processed, signals by status, open/closed trades, PnL, errors by adapter). Structured logging via Logback JSON encoder; correlation/signal/trade IDs propagated in logs via MDC.
- **Rationale**: Actuator/Micrometer are the default Spring observability stack; custom `HealthIndicator` beans keep adapter-level health inside adapters while the domain stays framework-free. Structured logs make the required event tracing auditable.
- **Alternatives considered**:
  - OpenTelemetry auto-instrumentation: more powerful but heavier than needed for v1; noted as a later option.

## Pending / Decision Log

- `trading_statistics` materialization: statistics are computed on demand from closed trades for v1; a materialized daily table is documented in `data-model.md` and can be introduced later without domain changes.
- Initial strategy: a configurable SMA + RSI combination is proposed as the replaceable initial strategy (see `plan.md`); exact indicator parameters are configuration, not architecture.
