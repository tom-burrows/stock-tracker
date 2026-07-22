CREATE TABLE alert_rules (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL,
    symbol            VARCHAR(20) NOT NULL,
    condition         VARCHAR(20) NOT NULL,
    threshold         NUMERIC(19,4) NOT NULL,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    last_triggered_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Consumed by alert-evaluation-service's hot-path query (findBySymbolAndActiveTrue)
CREATE INDEX idx_alert_rules_symbol_active ON alert_rules (symbol, active);
