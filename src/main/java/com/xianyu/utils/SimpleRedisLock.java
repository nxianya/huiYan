package com.xianyu.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
    private static final String LOCK_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private String lock;
    private Object value;

    public Object getValue() {
        return value;
    }

    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String lockName, Object value, StringRedisTemplate stringRedisTemplate) {
        this.lock = lockName;
        this.value = value;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public SimpleRedisLock(String lockName, StringRedisTemplate stringRedisTemplate) {
        this.lock = lockName;
        this.stringRedisTemplate = stringRedisTemplate;
    }


    @Override
    public boolean tryLock(long timeout, TimeUnit unit) {
        if (value == null) {
            this.value = ID_PREFIX + Thread.currentThread().getId();
        }
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + lock, value + "", timeout, unit);
        //考虑到拆装箱产生空指针的可能性
        return Boolean.TRUE.equals(flag);
    }

    @Override
    public void unLock() {
        //获取线程标识
        String id = stringRedisTemplate.opsForValue().get(LOCK_PREFIX + lock);
        if (value.equals(id)) {
            //释放锁
            stringRedisTemplate.delete(LOCK_PREFIX + lock);
        }
    }
}
