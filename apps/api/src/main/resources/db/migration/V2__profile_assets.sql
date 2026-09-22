CREATE TABLE investment_profile (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    questionnaire_version VARCHAR(40) NOT NULL,
    answers VARCHAR(30) NOT NULL,
    score INTEGER NOT NULL CHECK (score BETWEEN 5 AND 25),
    risk_level VARCHAR(30) NOT NULL,
    assessed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_profile_user_date ON investment_profile(user_id, assessed_at DESC);
CREATE TABLE asset (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    name VARCHAR(80) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('CASH','DEPOSIT','STOCK','FUND','BOND','PENSION','OTHER','DEBT')),
    amount NUMERIC(15,0) NOT NULL CHECK (amount > 0 AND amount <= 999999999999),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_asset_user ON asset(user_id);
