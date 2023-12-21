-- 使用脚本的目的是让判断和释放锁成为原子操作
if(redis.call('get',KETS[1]) == ARGV[1] ) then
if(redis.call('get',KETS[1]) == ARGV[1] ) then
    return redis.call('del', KETS[1])
end
return 0