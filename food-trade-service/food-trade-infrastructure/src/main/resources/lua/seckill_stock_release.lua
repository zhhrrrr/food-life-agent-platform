local stockKey = KEYS[1]
local userKey = KEYS[2]

local userId = ARGV[1]

-- An order may be replayed after another purchase by the same user.
-- A user counter alone cannot identify which order has already released stock.
if KEYS[3] and ARGV[2] then
    if redis.call('hsetnx', KEYS[3], ARGV[2], '1') == 0 then
        return {0, tonumber(redis.call('get', stockKey) or '-1')}
    end
    local ttl = redis.call('ttl', stockKey)
    if ttl > 0 then redis.call('expire', KEYS[3], ttl) end
end

local takeCount = tonumber(redis.call('hget', userKey, userId) or '0')
if takeCount <= 0 then
    local currentStock = tonumber(redis.call('get', stockKey) or '-1')
    return {0, currentStock}
end

local stock = -1
if redis.call('exists', stockKey) == 1 then
    stock = redis.call('incr', stockKey)
end

if takeCount <= 1 then
    redis.call('hdel', userKey, userId)
else
    redis.call('hincrby', userKey, userId, -1)
end

return {1, stock}
