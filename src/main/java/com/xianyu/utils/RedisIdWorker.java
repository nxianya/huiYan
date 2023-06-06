package com.xianyu.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Component
public class RedisIdWorker {
//    public static void main(String[] args) {
//        LocalDateTime time = LocalDateTime.of(2023, 1, 1, 0, 0, 0);
//        long second = time.toEpochSecond(ZoneOffset.UTC);
//        System.out.println(second);
//    }

    //时间戳
    public static final long BEGIN_TIMESTAMP=1672531200L;
    //序列号位数
    public static final int COUNT_BITS=32;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix){
        //时间戳31bit
        LocalDateTime now = LocalDateTime.now();
        long current = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = current-BEGIN_TIMESTAMP;
        //序列号32bit
        //添加日期字符串防止一直使用同一个key自增,理论上有达到上限的可能性(2^64),且序列号设定的上限为2^32
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date+":");
        stringRedisTemplate.expire("icr:" + keyPrefix + ":" + date+":",1L, TimeUnit.DAYS);
        //拼接
        return timeStamp<<COUNT_BITS|count;
    }
}
