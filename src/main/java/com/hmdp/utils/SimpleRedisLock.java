package com.hmdp.utils;


import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;


public class SimpleRedisLock implements ILock{
    private String name;
    private  StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    public boolean tryLock(long timeoutSec) {
        Long threadId = Thread.currentThread().getId();
        String newThreadId = ID_PREFIX + threadId;
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX+name,newThreadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    public void unlock() {
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        String newThreadId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if(threadId.equals(newThreadId)) {
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
