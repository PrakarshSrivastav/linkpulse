ALTER TABLE links
    ADD COLUMN owner_id BIGINT,
    ADD CONSTRAINT fk_links_owner FOREIGN KEY (owner_id) REFERENCES users (id);

CREATE INDEX idx_links_owner_created ON links (owner_id, created_at DESC);