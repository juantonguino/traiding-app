CREATE TABLE trading_state (
    id                       TINYINT       NOT NULL,
    mode                     VARCHAR(10)   NOT NULL,
    signals_enabled          BOOLEAN       NOT NULL,
    open_trade_id            BIGINT        NULL,
    open_symbol              VARCHAR(20)   NULL,
    emergency_active         BOOLEAN       NOT NULL,
    market_data_healthy      BOOLEAN       NOT NULL,
    last_candle_processed_at TIMESTAMP(6)  NULL,
    signals_disabled_by      VARCHAR(50)   NULL,
    signals_disabled_at      TIMESTAMP(6)  NULL,
    created_at               TIMESTAMP(6)  NOT NULL,
    updated_at               TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_trading_state_id CHECK (id = 1),
    CONSTRAINT fk_trading_state_open_trade FOREIGN KEY (open_trade_id) REFERENCES paper_trades (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
