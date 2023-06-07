package com.xianyu.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private static final String LOCK_PREFIX ="lock:";
    private String lock;
    private Object value;

    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String lockName,Object value, StringRedisTemplate stringRedisTemplate) {
        this.lock= lockName;
        this.value=value;
        this.stringRedisTemplate = stringRedisTemplate;
    }



    @Override
    public boolean tryLock(long timeout, TimeUnit unit) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + lock, value + "", timeout, unit);
        //考虑到拆装箱产生空指针的可能性
        return Boolean.TRUE.equals(flag);
    }

    @Override
    public void unLock() {
        stringRedisTemplate.delete(LOCK_PREFIX+lock);
    }
}
