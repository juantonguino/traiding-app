# Feature Specification: Trading Signal Backend

**Feature Branch**: `001-trading-signal-backend`

**Created**: 2026-08-14

**Status**: Draft

**Input**: User description: "Build a Binance Spot trading signal backend that analyzes the market and notifies buy and sell opportunities without executing real orders. The goal is to receive clear, auditable signals, simulate trades to measure their outcome, and receive Telegram notifications. The system must initially operate as PAPER TRADING and must not risk real capital."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Receive a buy signal (Priority: P1)

As a user, I want to receive a buy alert with the information needed to decide whether to consider the opportunity.

**Why this priority**: Receiving buy signals is the primary value of the product; without it the rest of the flow does not exist.

**Independent Test**: Can be fully tested by submitting one closed candle for a configured symbol and verifying that a single BUY signal with complete data is generated, recorded, and notified.

**Acceptance Scenarios**:

1. **Given** a configured symbol with a closed candle, **When** the candle is processed, **Then** a BUY signal is generated containing symbol, price, interval, strategy, and timestamp.
2. **Given** a generated BUY signal, **When** the signal is recorded, **Then** it is stored with state GENERATED and a Telegram notification is sent indicating PAPER TRADING.
3. **Given** an unclosed (in-progress) candle, **When** it is received, **Then** it does not produce a signal.

### User Story 2 - Open a simulated trade (Priority: P1)

As a user, I want a valid BUY signal to open a simulated trade so I can measure its outcome.

**Why this priority**: Simulating trades is the core mechanism to evaluate the strategy without risk.

**Independent Test**: Can be fully tested by processing a valid BUY signal when no trade is open and verifying a simulated trade is created and recorded.

**Acceptance Scenarios**:

1. **Given** no open trade exists, **When** a valid BUY signal is accepted, **Then** a simulated trade is opened with state OPEN.
2. **Given** an opened trade, **When** it is recorded, **Then** it stores symbol, entry price, simulated quantity, open timestamp, strategy, originating signal, and configured stop-loss/take-profit.
3. **Given** an opened trade, **When** it is created, **Then** an opening notification is sent.

### User Story 3 - Prevent simultaneous trades (Priority: P1)

As a user, I want the system to prevent new buys while a trade is open.

**Why this priority**: The single-open-trade rule is a core business constraint that protects the evaluation model.

**Independent Test**: Can be fully tested by opening a trade on BTCUSDT, submitting a BUY signal for ETHUSDT, and verifying it is ignored with the correct reason and notification.

**Acceptance Scenarios**:

1. **Given** an open trade exists on any symbol, **When** a new BUY signal arrives for any symbol, **Then** the signal is ignored.
2. **Given** an ignored signal, **When** it is recorded, **Then** it preserves the reason (existing open trade) and the symbol that originated the open trade.
3. **Given** the open trade closes, **When** a new BUY signal arrives, **Then** it can be accepted.

### User Story 4 - Close a simulated trade (Priority: P1)

As a user, I want the trade to close when a defined condition is met.

**Why this priority**: Closing completes the measurement loop; without it results and statistics cannot be produced.

**Independent Test**: Can be fully tested by triggering each close condition (SELL signal, stop-loss, take-profit, manual close, emergency) and verifying the trade closes with full result data.

**Acceptance Scenarios**:

1. **Given** an open trade, **When** a valid SELL signal for the traded symbol arrives, **Then** the trade closes with reason SELL_SIGNAL.
2. **Given** an open trade, **When** stop-loss or take-profit is reached, **Then** the trade closes with the corresponding reason.
3. **Given** a closed trade, **When** it is recorded, **Then** it stores exit price, close timestamp, close reason, duration, gross PnL, estimated costs, net PnL, return percentage, and final result (WIN, LOSS, or BREAK_EVEN).
4. **Given** a closed trade, **When** closing completes, **Then** a close notification is sent and a new BUY trade is permitted.

### User Story 5 - Consult gains and losses (Priority: P2)

As a user, I want to consult accumulated gains and losses to evaluate the strategy's performance.

**Why this priority**: Enables the evaluation that justifies the whole paper trading model.

**Independent Test**: Can be fully tested by closing several trades and querying accumulated and per-trade results.

**Acceptance Scenarios**:

1. **Given** closed trades, **When** results are consulted, **Then** each closed trade shows gross PnL, estimated costs, and net PnL.
2. **Given** historical data, **When** statistics are consulted, **Then** accumulated gains, accumulated losses, net result, win rate, average gain, average loss, and maximum drawdown (when available) are shown.
3. **Given** historical data, **When** statistics are filtered, **Then** results can be viewed by symbol, strategy, interval, day, and month.

### User Story 6 - Receive Telegram alerts (Priority: P2)

As a user, I want to receive signals and important events via Telegram.

**Why this priority**: Notification is the main delivery channel for a system the user does not watch continuously.

**Independent Test**: Can be fully tested by generating each event type and verifying the corresponding message is sent with sufficient data.

**Acceptance Scenarios**:

1. **Given** a BUY or SELL signal, **When** it is generated, **Then** a message is sent.
2. **Given** an ignored signal, an opening, or a closing, **When** the event occurs, **Then** a message is sent.
3. **Given** a transient Telegram failure, **When** a signal is processed, **Then** the signal is not lost and analysis continues.
4. **Given** any message, **When** it is sent, **Then** it indicates PAPER TRADING and never suggests a real order was executed.

### User Story 7 - Operate in safe mode (Priority: P1)

As a user, I want to be sure the system does not execute real operations.

**Why this priority**: Safety is non-negotiable; the product must never risk real capital.

**Independent Test**: Can be fully tested by inspecting the system state and verifying that no real order capability exists and all messages indicate simulation.

**Acceptance Scenarios**:

1. **Given** the running system, **When** its state is consulted, **Then** the mode is shown as PAPER TRADING.
2. **Given** any signal or trade event, **When** it is processed, **Then** no real order is created and no trading permission is required.
3. **Given** any notification, **When** it is sent, **Then** it clearly indicates the operation is simulated.

### User Story 8 - Recover the full history (Priority: P2)

As a user, I want to consult the complete history to audit the bot's decisions.

**Why this priority**: Auditability builds trust and allows reconstructing why each decision was made.

**Independent Test**: Can be fully tested by reviewing recorded data and reconstructing the outcome of each trade from the originating signal.

**Acceptance Scenarios**:

1. **Given** recorded history, **When** it is consulted, **Then** accepted and ignored signals are listed.
2. **Given** recorded history, **When** trades are consulted, **Then** open and closed trades are listed with their originating signal.
3. **Given** recorded history, **When** a trade is inspected, **Then** its open and close reasons and full result can be reconstructed.

### Edge Cases

- What happens when the same closed candle is received twice? Duplicate signals must not be produced.
- What happens when the market connection is lost and restored? The system must reconnect and detect the absence of data.
- What happens when a SELL signal arrives for a symbol with no open trade? No trade is created.
- What happens when a BUY signal arrives while a trade is open on a different symbol? The signal is ignored with the open-trade reason.
- What happens when the system restarts with an open trade? The open trade must be recovered from persisted state.
- What happens when data is invalid or missing? The signal must not be generated and the system must stop new entries until a reliable state is recovered.
- What happens when an emergency mode is activated? The open trade is closed and new entries are blocked.
- What happens when the strategy is replaced? Historical signals and trades remain intact and comparable.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow configuring the symbols to analyze, the candle interval, the active strategy, strategy parameters, estimated commission percentage, estimated slippage percentage, and optional stop-loss and take-profit parameters.
- **FR-002**: System MUST process only closed candles; in-progress candles MUST NOT generate signals.
- **FR-003**: System MUST identify each candle by symbol, interval, and open time, and MUST NOT generate duplicate signals when the same candle is processed more than once.
- **FR-004**: System MUST generate signals that include at minimum symbol, signal type (BUY, SELL, or HOLD), reference price, timestamp, interval, generating strategy, confidence level (when provided by the strategy), reason, and optional stop-loss and take-profit.
- **FR-005**: System MUST track each signal with a state: GENERATED, ACCEPTED, IGNORED, USED_TO_OPEN, USED_TO_CLOSE, or EXPIRED.
- **FR-006**: System MUST allow at most one open simulated trade globally across all symbols and strategies.
- **FR-007**: System MUST reject a BUY signal when a trade is already open, record the signal as ignored with the reason GLOBAL_TRADE_ALREADY_OPEN, and notify the user of the open trade's symbol.
- **FR-008**: System MUST accept a new BUY only after the current trade has been closed correctly.
- **FR-009**: System MUST NOT create a trade from a SELL signal when no matching open trade exists.
- **FR-010**: System MUST create a simulated trade from an accepted BUY signal recording symbol, entry price, simulated quantity, open timestamp, strategy, originating signal, stop-loss/take-profit, and state OPEN.
- **FR-011**: System MUST close an open trade when any of the following occurs: a valid SELL signal for the traded symbol, stop-loss reached, take-profit reached, an authorized manual close, activation of the global emergency mechanism, or a configured expiration condition.
- **FR-012**: System MUST record for every closed trade: exit price, close timestamp, close reason, duration, gross PnL, estimated fees, estimated slippage, net PnL, return percentage, and final result (WIN, LOSS, or BREAK_EVEN).
- **FR-013**: System MUST calculate trade results from entry price, exit price, simulated quantity, entry and exit commissions, estimated slippage, trade direction, and quote currency, distinguishing gross result, estimated costs, net result, and return percentage.
- **FR-014**: System MUST use sufficient precision for monetary values so financial results are not incorrect.
- **FR-015**: System MUST allow querying all generated signals, all ignored signals, all open and closed trades, each trade's result, accumulated gains, accumulated losses, accumulated net result, win rate, average gain, average loss, maximum drawdown (when available), and results by symbol, strategy, interval, day, and month.
- **FR-016**: System MUST allow reconstructing from history why each signal was generated, accepted, ignored, opened, or closed.
- **FR-017**: System MUST send Telegram notifications for: a new BUY signal, a new SELL signal, an ignored BUY (because a trade is open), a simulated trade opening, a simulated trade closing, a recorded gain or loss, critical system errors, loss of market data connection, activation of emergency mode, and periodic result summaries.
- **FR-018**: System MUST include in BUY notifications at minimum: symbol, price, interval, strategy, stop-loss, take-profit, confidence or reason, and PAPER TRADING status.
- **FR-019**: System MUST include in close notifications at minimum: symbol, entry, exit, gross result, commissions, slippage, net result, return percentage, close reason, and duration.
- **FR-020**: System MUST allow the user to query system state, whether a trade is open, the last processed signal, and whether market data is still being received.
- **FR-021**: System MUST allow enabling and disabling signal generation. When disabled, no new trades are opened, the open trade continues to be monitored, and the system records who or what process disabled signals and when.
- **FR-022**: System MUST allow a manual close of the current simulated trade and activation of an emergency mode.
- **FR-023**: System MUST NOT duplicate trades under retries and MUST remain consistent between recorded signals and trades.
- **FR-024**: System MUST recover the open-trade state after a restart from persisted state.
- **FR-025**: A transient Telegram failure MUST NOT alter a trade's result or stop the analysis.
- **FR-026**: A critical data failure MUST prevent new entries until a reliable state is recovered.
- **FR-027**: All signals and trades MUST carry a correlatable identifier for end-to-end tracing.

### Key Entities

- **Candle**: A closed market candle identified by symbol, interval, and open time, with price and volume data.
- **Signal**: A BUY, SELL, or HOLD decision produced by a strategy with price, timestamp, interval, strategy, confidence, reason, and optional stop-loss/take-profit.
- **SignalState**: The lifecycle state of a signal (GENERATED, ACCEPTED, IGNORED, USED_TO_OPEN, USED_TO_CLOSE, EXPIRED).
- **PaperTrade**: A simulated trade with symbol, quantity, entry and exit prices, timestamps, strategy, originating signal, stop-loss/take-profit, and state (OPEN, CLOSED, IGNORED).
- **TradeResult**: The financial outcome of a closed trade: gross PnL, estimated costs (fees and slippage), net PnL, return percentage, and final result (WIN, LOSS, BREAK_EVEN).
- **CloseReason**: The reason a trade closed (SELL_SIGNAL, STOP_LOSS, TAKE_PROFIT, MANUAL_CLOSE, EMERGENCY, EXPIRATION).
- **IgnoredSignal**: A signal that was rejected together with the reason for rejection.
- **StrategyConfig**: The active strategy and its configurable parameters.
- **TradingConfig**: The configured symbols, intervals, commission percentage, slippage percentage, and optional stop-loss/take-profit defaults.
- **Statistics**: Aggregated results (gains, losses, net result, win rate, averages, drawdown) filterable by symbol, strategy, interval, and period.
- **SystemState**: The current system mode (PAPER TRADING), signal-generation enabled/disabled status, whether a trade is open, and market-data health.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of signals are generated exclusively from closed candles.
- **SC-002**: The system never has more than one open trade at any moment; zero violations across concurrent signals and retries.
- **SC-003**: 100% of BUY signals arriving while a trade is open are recorded as ignored with the open-trade reason and notified.
- **SC-004**: 100% of closed trades are stored with gross PnL, estimated costs, net PnL, return percentage, and final result, reconstructable from recorded history without external data.
- **SC-005**: The system tolerates a transient Telegram failure without losing a signal or altering a trade's result.
- **SC-006**: The system restarts without losing the state of an open trade.
- **SC-007**: No duplicate signals or duplicate trades are produced when the same candle or the same operation is retried.
- **SC-008**: Users can obtain statistics filtered by symbol, strategy, interval, day, and month and distinguish gross result, costs, net result, and return percentage.
- **SC-009**: All relevant events (signal generation, acceptance, rejection, trade opening, and closing) are traceable via a correlatable identifier.
- **SC-010**: Every notification clearly indicates PAPER TRADING and never suggests a real order was executed.

## Assumptions

- The system operates exclusively in PAPER TRADING mode for the first version; no real orders are executed.
- Market data comes from public Binance Spot data.
- The base quote currency is USDT for all configured symbols.
- Commission and slippage are expressed as configurable percentages and estimated consistently for entry and exit.
- Stop-loss and take-profit are optional and, when not configured, the trade relies on the other close conditions.
- A manual close is an authorized administrative action.
- A global emergency mechanism exists and, when activated, closes the open trade and blocks new entries.
- A configured expiration condition may close a trade after a configurable period.
- The strategy is replaceable; replacing it does not alter historical data.
- Binance, Telegram, and the clock can be substituted by test doubles without changing business rules.
- The strategy provides signals with the minimum data required; confidence is included when the strategy supports it.
- A periodic result summary is configurable.
- An existing open trade is the source of truth for the single-open-trade rule and is recovered on restart.
