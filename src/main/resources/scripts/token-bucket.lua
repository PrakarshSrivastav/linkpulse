-- Token bucket, evaluated atomically inside Redis (no other command can run between the read and the write).
-- KEYS[1]  bucket key, e.g. rl:login:203.0.113.7
-- ARGV[1]  capacity: the burst size, and the most tokens the bucket can hold
-- ARGV[2]  refill period in ms: an empty bucket is full again after this long
-- Returns { allowed (1 or 0), milliseconds until the next token if refused }
local capacity = tonumber(ARGV[1])
local period_ms = tonumber(ARGV[2])
local rate = capacity / period_ms  -- tokens per millisecond

-- Redis' own clock, so every app instance agrees on "now" even if their clocks drift.
local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)

local state = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(state[1]) or capacity
local ts = tonumber(state[2]) or now

tokens = math.min(capacity, tokens + (now - ts) * rate)

local allowed = 0
local retry_ms = 0
if tokens >= 1 then
  allowed = 1
  tokens = tokens - 1
else
  retry_ms = math.ceil((1 - tokens) / rate)
end

redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'ts', tostring(now))
-- An idle bucket is full again after period_ms, so there is no reason to keep it longer.
redis.call('PEXPIRE', KEYS[1], period_ms + 1000)
return { allowed, retry_ms }
