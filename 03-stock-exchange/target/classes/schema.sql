-- Stock Exchange Database Schema
-- PostgreSQL 15+

-- =============================================
-- EXTENSIONS
-- =============================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm"; -- For text search

-- =============================================
-- CLIENTS TABLE
-- =============================================

CREATE TABLE IF NOT EXISTS clients (
    client_id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    client_type VARCHAR(50) NOT NULL DEFAULT 'RETAIL',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    api_key VARCHAR(64) UNIQUE,
    api_secret VARCHAR(128),
    rate_limit_per_second INTEGER DEFAULT 100,
    max_orders_per_day INTEGER DEFAULT 10000,
    can_trade BOOLEAN DEFAULT TRUE,
    is_market_maker BOOLEAN DEFAULT FALSE,
    has_dma_access BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP,
    
    CONSTRAINT chk_client_type CHECK (client_type IN ('RETAIL', 'INSTITUTIONAL', 'MARKET_MAKER', 'BROKER_DEALER', 'PROPRIETARY')),
    CONSTRAINT chk_client_status CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE INDEX idx_clients_api_key ON clients(api_key);
CREATE INDEX idx_clients_status ON clients(status);
CREATE INDEX idx_clients_type ON clients(client_type);

-- =============================================
-- ACCOUNTS TABLE
-- =============================================

CREATE TABLE IF NOT EXISTS accounts (
    account_id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL REFERENCES clients(client_id),
    account_number VARCHAR(50) NOT NULL UNIQUE,
    account_type VARCHAR(50) NOT NULL DEFAULT 'CASH',
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    
    -- Balances
    cash_balance DECIMAL(20, 4) DEFAULT 0,
    buying_power DECIMAL(20, 4) DEFAULT 0,
    margin_used DECIMAL(20, 4) DEFAULT 0,
    margin_available DECIMAL(20, 4) DEFAULT 0,
    equity DECIMAL(20, 4) DEFAULT 0,
    
    -- P&L
    realized_pnl DECIMAL(20, 4) DEFAULT 0,
    unrealized_pnl DECIMAL(20, 4) DEFAULT 0,
    
    -- Risk Limits
    daily_loss_limit DECIMAL(20, 4) DEFAULT 50000,
    max_position_size BIGINT DEFAULT 100000,
    max_order_value DECIMAL(20, 4) DEFAULT 1000000,
    rate_limit_per_second INTEGER DEFAULT 100,
    
    -- Flags
    can_trade BOOLEAN DEFAULT TRUE,
    margin_enabled BOOLEAN DEFAULT FALSE,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP,
    
    CONSTRAINT chk_account_type CHECK (account_type IN ('CASH', 'MARGIN', 'IRA', 'INSTITUTIONAL')),
    CONSTRAINT chk_account_status CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE INDEX idx_accounts_client_id ON accounts(client_id);
CREATE INDEX idx_accounts_status ON accounts(status);
CREATE INDEX idx_accounts_account_number ON accounts(account_number);

-- =============================================
-- INSTRUMENTS TABLE
-- =============================================

CREATE TABLE IF NOT EXISTS instruments (
    instrument_id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'STOCK',
    exchange VARCHAR(50) DEFAULT 'NYSE',
    currency VARCHAR(3) DEFAULT 'USD',
    
    -- Trading Parameters
    tick_size DECIMAL(10, 6) DEFAULT 0.01,
    lot_size INTEGER DEFAULT 1,
    min_quantity INTEGER DEFAULT 1,
    max_quantity INTEGER DEFAULT 10000000,
    
    -- Circuit Breakers
    circuit_breaker_up DECIMAL(5, 4) DEFAULT 0.10,
    circuit_breaker_down DECIMAL(5, 4) DEFAULT 0.10,
    
    -- Market Data
    last_price DECIMAL(20, 4),
    previous_close DECIMAL(20, 4),
    open DECIMAL(20, 4),
    high DECIMAL(20, 4),
    low DECIMAL(20, 4),
    volume BIGINT DEFAULT 0,
    value_traded DECIMAL(20, 4) DEFAULT 0,
    
    -- Status
    trading_status VARCHAR(50) DEFAULT 'CLOSED',
    tradable BOOLEAN DEFAULT TRUE,
    halt_reason VARCHAR(255),
    
    last_trade_time TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_instrument_type CHECK (type IN ('STOCK', 'ETF', 'OPTION', 'FUTURE', 'FOREX', 'CRYPTO', 'BOND', 'INDEX')),
    CONSTRAINT chk_trading_status CHECK (trading_status IN ('PRE_MARKET', 'OPEN', 'HALTED', 'AUCTION', 'CLOSED', 'AFTER_HOURS'))
);

CREATE INDEX idx_instruments_symbol ON instruments(symbol);
CREATE INDEX idx_instruments_type ON instruments(type);
CREATE INDEX idx_instruments_trading_status ON instruments(trading_status);
CREATE INDEX idx_instruments_tradable ON instruments(tradable);

-- =============================================
-- ORDERS TABLE
-- =============================================

CREATE TABLE IF NOT EXISTS orders (
    order_id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL REFERENCES clients(client_id),
    account_id BIGINT NOT NULL REFERENCES accounts(account_id),
    instrument_id BIGINT REFERENCES instruments(instrument_id),
    symbol VARCHAR(20) NOT NULL,
    
    -- Order Details
    side VARCHAR(10) NOT NULL,
    order_type VARCHAR(20) NOT NULL,
    time_in_force VARCHAR(10) NOT NULL DEFAULT 'DAY',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_NEW',
    
    -- Quantities
    quantity BIGINT NOT NULL,
    filled_quantity BIGINT DEFAULT 0,
    remaining_quantity BIGINT NOT NULL,
    display_quantity BIGINT,
    
    -- Prices
    price DECIMAL(20, 4),
    stop_price DECIMAL(20, 4),
    average_price DECIMAL(20, 4),
    
    -- IDs
    client_order_id VARCHAR(100),
    
    -- Timestamps
    expire_time TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    submit_time BIGINT,
    match_time BIGINT,
    
    -- Rejection
    reject_reason VARCHAR(500),
    
    -- Statistics
    fill_count INTEGER DEFAULT 0,
    
    CONSTRAINT chk_order_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT chk_order_type CHECK (order_type IN ('MARKET', 'LIMIT', 'STOP', 'STOP_LIMIT', 'TRAILING_STOP', 'ICEBERG', 'TWAP', 'VWAP')),
    CONSTRAINT chk_order_tif CHECK (time_in_force IN ('DAY', 'GTC', 'IOC', 'FOK', 'GTD', 'OPG', 'CLO')),
    CONSTRAINT chk_order_status CHECK (status IN ('PENDING_NEW', 'NEW', 'PARTIALLY_FILLED', 'FILLED', 'PENDING_CANCEL', 'CANCELLED', 'REJECTED', 'EXPIRED', 'SUSPENDED'))
);

CREATE INDEX idx_orders_client_id ON orders(client_id);
CREATE INDEX idx_orders_account_id ON orders(account_id);
CREATE INDEX idx_orders_symbol ON orders(symbol);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at);
CREATE INDEX idx_orders_client_order_id ON orders(client_order_id);
CREATE INDEX idx_orders_active ON orders(client_id, status) WHERE status IN ('NEW', 'PARTIALLY_FILLED', 'PENDING_NEW', 'PENDING_CANCEL');

-- =============================================
-- TRADES TABLE
-- =============================================

CREATE TABLE IF NOT EXISTS trades (
    trade_id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    
    -- Trade Details
    price DECIMAL(20, 4) NOT NULL,
    quantity BIGINT NOT NULL,
    value DECIMAL(20, 4) NOT NULL,
    
    -- Orders
    buy_order_id BIGINT NOT NULL REFERENCES orders(order_id),
    sell_order_id BIGINT NOT NULL REFERENCES orders(order_id),
    
    -- Participants
    buyer_account_id BIGINT NOT NULL REFERENCES accounts(account_id),
    seller_account_id BIGINT NOT NULL REFERENCES accounts(account_id),
    
    -- Aggressor
    aggressor_side VARCHAR(10),
    
    -- Commissions
    buyer_commission DECIMAL(20, 4) DEFAULT 0,
    seller_commission DECIMAL(20, 4) DEFAULT 0,
    
    -- Settlement
    settlement_date DATE,
    settlement_status VARCHAR(20) DEFAULT 'PENDING',
    
    executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_trade_aggressor CHECK (aggressor_side IN ('BUY', 'SELL')),
    CONSTRAINT chk_settlement_status CHECK (settlement_status IN ('PENDING', 'SETTLED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_trades_symbol ON trades(symbol);
CREATE INDEX idx_trades_buy_order_id ON trades(buy_order_id);
CREATE INDEX idx_trades_sell_order_id ON trades(sell_order_id);
CREATE INDEX idx_trades_buyer_account ON trades(buyer_account_id);
CREATE INDEX idx_trades_seller_account ON trades(seller_account_id);
CREATE INDEX idx_trades_executed_at ON trades(executed_at);
CREATE INDEX idx_trades_settlement ON trades(settlement_date, settlement_status);

-- =============================================
-- POSITIONS TABLE
-- =============================================

CREATE TABLE IF NOT EXISTS positions (
    position_id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(account_id),
    symbol VARCHAR(20) NOT NULL,
    
    -- Position Details
    quantity BIGINT NOT NULL DEFAULT 0,
    side VARCHAR(10) DEFAULT 'FLAT',
    
    -- Costs
    average_cost DECIMAL(20, 4) DEFAULT 0,
    cost_basis DECIMAL(20, 4) DEFAULT 0,
    
    -- Market Values
    market_price DECIMAL(20, 4),
    market_value DECIMAL(20, 4) DEFAULT 0,
    previous_close DECIMAL(20, 4),
    
    -- P&L
    unrealized_pnl DECIMAL(20, 4) DEFAULT 0,
    realized_pnl DECIMAL(20, 4) DEFAULT 0,
    today_pnl DECIMAL(20, 4) DEFAULT 0,
    
    opened_at TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uq_position_account_symbol UNIQUE (account_id, symbol),
    CONSTRAINT chk_position_side CHECK (side IN ('LONG', 'SHORT', 'FLAT'))
);

CREATE INDEX idx_positions_account_id ON positions(account_id);
CREATE INDEX idx_positions_symbol ON positions(symbol);
CREATE INDEX idx_positions_open ON positions(account_id) WHERE quantity != 0;

-- =============================================
-- AUDIT LOG TABLE
-- =============================================

CREATE TABLE IF NOT EXISTS audit_log (
    log_id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    old_value JSONB,
    new_value JSONB,
    client_id BIGINT,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);

-- =============================================
-- SEED DATA - Sample Instruments
-- =============================================

INSERT INTO instruments (symbol, name, type, exchange, tick_size, lot_size, previous_close, trading_status, tradable) VALUES
    ('AAPL', 'Apple Inc.', 'STOCK', 'NASDAQ', 0.01, 1, 175.50, 'CLOSED', TRUE),
    ('GOOGL', 'Alphabet Inc.', 'STOCK', 'NASDAQ', 0.01, 1, 141.25, 'CLOSED', TRUE),
    ('MSFT', 'Microsoft Corporation', 'STOCK', 'NASDAQ', 0.01, 1, 378.90, 'CLOSED', TRUE),
    ('AMZN', 'Amazon.com Inc.', 'STOCK', 'NASDAQ', 0.01, 1, 178.50, 'CLOSED', TRUE),
    ('TSLA', 'Tesla Inc.', 'STOCK', 'NASDAQ', 0.01, 1, 248.75, 'CLOSED', TRUE),
    ('META', 'Meta Platforms Inc.', 'STOCK', 'NASDAQ', 0.01, 1, 505.25, 'CLOSED', TRUE),
    ('NVDA', 'NVIDIA Corporation', 'STOCK', 'NASDAQ', 0.01, 1, 875.50, 'CLOSED', TRUE),
    ('JPM', 'JPMorgan Chase & Co.', 'STOCK', 'NYSE', 0.01, 1, 198.75, 'CLOSED', TRUE),
    ('V', 'Visa Inc.', 'STOCK', 'NYSE', 0.01, 1, 275.80, 'CLOSED', TRUE),
    ('WMT', 'Walmart Inc.', 'STOCK', 'NYSE', 0.01, 1, 165.25, 'CLOSED', TRUE),
    ('SPY', 'SPDR S&P 500 ETF', 'ETF', 'NYSE', 0.01, 1, 505.50, 'CLOSED', TRUE),
    ('QQQ', 'Invesco QQQ Trust', 'ETF', 'NASDAQ', 0.01, 1, 435.75, 'CLOSED', TRUE),
    ('BTC-USD', 'Bitcoin', 'CRYPTO', 'CRYPTO', 0.01, 0.001, 67500.00, 'CLOSED', TRUE),
    ('ETH-USD', 'Ethereum', 'CRYPTO', 'CRYPTO', 0.01, 0.001, 3500.00, 'CLOSED', TRUE)
ON CONFLICT (symbol) DO NOTHING;

-- =============================================
-- SEED DATA - Sample Client and Account
-- =============================================

INSERT INTO clients (email, name, client_type, api_key, api_secret, rate_limit_per_second) VALUES
    ('demo@stockexchange.com', 'Demo Trader', 'RETAIL', 'demo-api-key-12345', 'demo-secret-67890', 100),
    ('market-maker@stockexchange.com', 'Market Maker LLC', 'MARKET_MAKER', 'mm-api-key-12345', 'mm-secret-67890', 1000)
ON CONFLICT (email) DO NOTHING;

INSERT INTO accounts (client_id, account_number, account_type, cash_balance, buying_power, equity, daily_loss_limit, max_position_size)
SELECT client_id, 'ACCT-' || client_id || '-001', 'CASH', 100000.00, 100000.00, 100000.00, 10000.00, 50000
FROM clients WHERE email = 'demo@stockexchange.com'
ON CONFLICT (account_number) DO NOTHING;

INSERT INTO accounts (client_id, account_number, account_type, cash_balance, buying_power, equity, daily_loss_limit, max_position_size, margin_enabled)
SELECT client_id, 'ACCT-' || client_id || '-001', 'MARGIN', 10000000.00, 20000000.00, 10000000.00, 500000.00, 1000000, TRUE
FROM clients WHERE email = 'market-maker@stockexchange.com'
ON CONFLICT (account_number) DO NOTHING;

-- =============================================
-- FUNCTIONS
-- =============================================

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply trigger to tables
CREATE TRIGGER update_clients_updated_at BEFORE UPDATE ON clients FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_accounts_updated_at BEFORE UPDATE ON accounts FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_instruments_updated_at BEFORE UPDATE ON instruments FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_orders_updated_at BEFORE UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_positions_updated_at BEFORE UPDATE ON positions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
