CREATE TABLE links (
                       id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       code        VARCHAR(32)   NOT NULL,
                       long_url    VARCHAR(2048) NOT NULL,
                       created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
                       expires_at  TIMESTAMPTZ,
                       CONSTRAINT uk_links_code UNIQUE (code)
);