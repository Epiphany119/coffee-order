-- KEYS[1] = 秒杀库存；KEYS[2] = 用户限购标记；ARGV[1] = 标记过期秒数
-- 返回：1 成功，0 售罄，2 已抢过。检查、扣减、限购标记在 Redis 单线程中原子完成。
if redis.call('EXISTS', KEYS[2]) == 1 then
  return 2
end
local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
if stock <= 0 then
  return 0
end
redis.call('DECR', KEYS[1])
redis.call('SET', KEYS[2], '1', 'EX', ARGV[1])
return 1
