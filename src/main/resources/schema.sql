CREATE TABLE IF NOT EXISTS matches (
                                       id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255),
    primary_provider VARCHAR(32) NOT NULL
    );

CREATE TABLE IF NOT EXISTS provider_match_map (
                                                  provider VARCHAR(32) NOT NULL,
    provider_match_id VARCHAR(64) NOT NULL,
    match_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (provider, provider_match_id)
    );

CREATE TABLE IF NOT EXISTS delivery_log (
                                            provider VARCHAR(32) NOT NULL,
    delivery_id VARCHAR(128) NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (provider, delivery_id)
    );

CREATE TABLE IF NOT EXISTS snapshots (
                                         match_id VARCHAR(64) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    seq BIGINT NOT NULL,
    rev INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    innings INT NOT NULL,
    runs INT NOT NULL,
    wickets INT NOT NULL,
    overs VARCHAR(16) NOT NULL,
    as_of TIMESTAMP,
    PRIMARY KEY (match_id, provider)
    );

CREATE TABLE IF NOT EXISTS commentary (
                                          match_id VARCHAR(64) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    ball_id VARCHAR(128) NOT NULL,
    seq BIGINT NOT NULL,
    rev INT NOT NULL,
    innings INT NOT NULL,
    over_no INT NOT NULL,
    ball_no INT NOT NULL,
    commentary_text VARCHAR(2000) NOT NULL,
    occurred_at TIMESTAMP,
    PRIMARY KEY (match_id, provider, ball_id)
    );

CREATE INDEX IF NOT EXISTS idx_commentary_order ON commentary (match_id, provider, seq);