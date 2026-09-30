-- KEYS: stock, buyers, reservation, deadline; ARGV: action, reservationId
local types = {'string', 'hash', 'hash', 'zset'}
for i=1,4 do
  local t = redis.call('TYPE', KEYS[i]).ok
  if t ~= 'none' and t ~= types[i] then return -1 end
end
local status = redis.call('HGET', KEYS[3], 'status')
if not status then return -1 end
local action = ARGV[1]
if action == 'CONFIRM' then
  if status ~= 'RESERVED' then return 0 end
  redis.call('HSET', KEYS[3], 'status', 'CONFIRMED')
elseif action == 'ROLLBACK' then
  if status ~= 'RESERVED' then return 0 end
  local value = redis.call('GET', KEYS[1])
  if not value or not tonumber(value) then return -1 end
  -- Read buyer type and owner before changing inventory: Lua errors do not roll back writes.
  local user = redis.call('HGET', KEYS[3], 'userId')
  local owner = redis.call('HGET', KEYS[2], user)
  redis.call('INCR', KEYS[1])
  if owner == ARGV[2] then redis.call('HDEL', KEYS[2], user) end
  redis.call('HSET', KEYS[3], 'status', 'ROLLED_BACK')
elseif action == 'CLOSE' then
  if status ~= 'RESERVED' and status ~= 'CONFIRMED' then return 0 end
  local value = redis.call('GET', KEYS[1])
  if not value or not tonumber(value) then return -1 end
  redis.call('INCR', KEYS[1])
  redis.call('HSET', KEYS[3], 'status', 'CLOSED')
  -- Keep the buyer: the policy is one persisted order per user/SKU, including closed orders.
else return -1 end
redis.call('ZREM', KEYS[4], ARGV[2])
redis.call('PEXPIRE', KEYS[3], 604800000)
return 1
