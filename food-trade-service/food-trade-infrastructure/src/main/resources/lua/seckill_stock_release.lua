local stockKey = KEYS[1]
local userKey = KEYS[2]

local userId = ARGV[1]

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
