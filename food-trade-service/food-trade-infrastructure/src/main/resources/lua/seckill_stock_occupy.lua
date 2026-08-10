local activityKey = KEYS[1]
local stockKey = KEYS[2]
local userKey = KEYS[3]

local userId = ARGV[1]
local now = tonumber(ARGV[2])

if redis.call('exists', activityKey) == 0 then
    return {-1, -1}
end

local status = redis.call('hget', activityKey, 'status')
local startTime = tonumber(redis.call('hget', activityKey, 'startTime'))
local endTime = tonumber(redis.call('hget', activityKey, 'endTime'))
local takeLimit = tonumber(redis.call('hget', activityKey, 'takeLimit'))

if status ~= '1' then
    return {-2, -1}
end
if startTime > now then
    return {-3, -1}
end
if endTime <= now then
    return {-4, -1}
end

local stock = tonumber(redis.call('get', stockKey) or '-1')
if stock <= 0 then
    return {-5, stock}
end

local takeCount = tonumber(redis.call('hget', userKey, userId) or '0')
if takeCount >= takeLimit then
    return {-6, stock}
end

local remainingStock = redis.call('decr', stockKey)
if remainingStock < 0 then
    redis.call('incr', stockKey)
    return {-5, 0}
end

redis.call('hincrby', userKey, userId, 1)
return {1, remainingStock}
