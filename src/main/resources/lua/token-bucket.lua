-- One global bucket per SKU. Redis TIME avoids caller clock skew.
local tm = redis.call('TIME')
local now = tonumber(tm[1]) * 1000 + math.floor(tonumber(tm[2]) / 1000)
local rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local tokens = tonumber(redis.call('HGET', KEYS[1], 'tokens')) or capacity
local last = tonumber(redis.call('HGET', KEYS[1], 'last')) or now
tokens = math.min(capacity, tokens + math.max(0, now-last) * rate / 1000)
local accepted = 0
if tokens >= 1 then tokens = tokens - 1; accepted = 1 end
redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'last', tostring(now))
redis.call('PEXPIRE', KEYS[1], math.ceil(capacity/rate*1000)+1000)
return accepted
