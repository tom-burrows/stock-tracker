CREATE TABLE notifications (
    id                BIGSERIAL PRIMARY KEY,
    rule_id           BIGINT NOT NULL,
    user_id           BIGINT NOT NULL,
    symbol            VARCHAR(20) NOT NULL,
    condition         VARCHAR(20) NOT NULL,
    threshold         NUMERIC(19,4) NOT NULL,
    observed_price    NUMERIC(19,4) NOT NULL,
    triggered_at      TIMESTAMPTZ NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_id ON notifications (user_id);
