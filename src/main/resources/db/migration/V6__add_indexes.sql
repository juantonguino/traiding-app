CREATE INDEX idx_signals_status ON signals (status);
CREATE INDEX idx_signals_symbol_time ON signals (symbol, candle_open_time);
CREATE INDEX idx_trades_status ON paper_trades (status);
CREATE INDEX idx_trades_symbol_open ON paper_trades (symbol, open_time);
CREATE INDEX idx_trades_close ON paper_trades (close_time);
CREATE INDEX idx_trades_open_signal ON paper_trades (open_signal_id);
