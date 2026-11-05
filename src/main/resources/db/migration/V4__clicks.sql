CREATE TABLE clicks (
                        id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        code        VARCHAR(32)   NOT NULL,
                        clicked_at  TIMESTAMPTZ   NOT NULL,
                        referrer    VARCHAR(512)
);

CREATE INDEX idx_clicks_code_time ON clicks (code, clicked_at);