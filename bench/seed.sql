INSERT INTO links (code, long_url)
SELECT 'b' || lpad(i::text, 6, '0'),
       'https://example.com/article/' || i
FROM generate_series(1, 100000) AS i
    ON CONFLICT (code) DO NOTHING;

ANALYZE links;