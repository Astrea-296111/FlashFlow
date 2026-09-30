-- KEYS: stock, buyers, reservation, deadline, metadata. All share one SKU hash tag.
-- ARGV: userId, reservationId, skuId, ttlMillis
local function type_ok(key, expected)
  local t = redis.call('TYPE', key).ok
  return t == 'none' or t == expected
end
if not type_ok(KEYS[1], 'string') or not type_ok(KEYS[2], 'hash') or
   not type_ok(KEYS[3], 'hash') or not type_ok(KEYS[4], 'zset') or
   not type_ok(KEYS[5], 'hash') then return {'CORRUPT_STATE'} end
local stock = tonumber(redis.call('GET', KEYS[1]))
local start = tonumber(redis.call('HGET', KEYS[5], 'start'))
local finish = tonumber(redis.call('HGET', KEYS[5], 'finish'))
local event = redis.call('HGET', KEYS[5], 'eventId')
if not stock or not start or not finish or not event then return {'NOT_INITIALIZED'} end
if stock < 0 or stock ~= math.floor(stock) then return {'CORRUPT_STATE'} end
local tm = redis.call('TIME')
local now = tonumber(tm[1]) * 1000 + math.floor(tonumber(tm[2]) / 1000)
if now < start or now >= finish or redis.call('HGET', KEYS[5], 'enabled') ~= '1' then return {'NOT_ON_SALE'} end
local previous = redis.call('HGET', KEYS[2], ARGV[1])
if previous then return {'DUPLICATE', previous} end
if stock == 0 then return {'SOLD_OUT'} end
if redis.call('EXISTS', KEYS[3]) == 1 then return {'CORRUPT_STATE'} end
local deadline = now + tonumber(ARGV[4])
redis.call('DECR', KEYS[1])
redis.call('HSET', KEYS[2], ARGV[1], ARGV[2])
redis.call('HSET', KEYS[3], 'status', 'RESERVED', 'userId', ARGV[1], 'skuId', ARGV[3],
  'eventId', event, 'createdAt', tostring(now), 'expiresAt', tostring(deadline))
redis.call('ZADD', KEYS[4], deadline, ARGV[2])
return {'RESERVED', ARGV[2], event, tostring(now), tostring(deadline)}
