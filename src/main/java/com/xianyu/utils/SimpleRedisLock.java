package com.xianyu.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
    private static final String LOCK_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
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
        //调用lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(LOCK_PREFIX + lock),
                ID_PREFIX + Thread.currentThread().getId());
    }

//    @Override
//    public void unLock() {
//        //获取线程标识
//        String id = stringRedisTemplate.opsForValue().get(LOCK_PREFIX + lock);
//        if (value.equals(id)) {
//            //释放锁
//            stringRedisTemplate.delete(LOCK_PREFIX + lock);
//        }
//    }
}
