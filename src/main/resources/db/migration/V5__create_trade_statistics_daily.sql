CREATE TABLE trade_statistics_daily (
    day           DATE          NOT NULL,
    symbol        VARCHAR(20)   NOT NULL,
    timeframe     VARCHAR(10)   NOT NULL,
    strategy      VARCHAR(50)   NOT NULL,
    closed_count  INT           NOT NULL DEFAULT 0,
    win_count     INT           NOT NULL DEFAULT 0,
    loss_count    INT           NOT NULL DEFAULT 0,
    gross_pnl     DECIMAL(24,8) NOT NULL DEFAULT 0,
    fees          DECIMAL(24,8) NOT NULL DEFAULT 0,
    net_pnl       DECIMAL(24,8) NOT NULL DEFAULT 0,
    PRIMARY KEY (day, symbol, timeframe, strategy)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
