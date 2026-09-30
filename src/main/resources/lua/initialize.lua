-- KEYS: stock, metadata, buyers, deadline. Initial BASELINE->ASYNC warmup only.
-- Existing state may belong to an earlier database generation: fail closed.
for i=1,#KEYS do
  if redis.call('EXISTS', KEYS[i]) == 1 then return -1 end
end
redis.call('SET', KEYS[1], ARGV[1])
redis.call('HSET', KEYS[2], 'eventId', ARGV[2], 'start', ARGV[3], 'finish', ARGV[4], 'enabled', '1')
return 1
