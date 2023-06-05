package com.xianyu.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xianyu.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.xianyu.utils.RedisConstants.*;

//封装缓存工具
@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public  void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public  void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <T,R> T queryWithPassThrough(String keyPrefix, R id, Class<T> type, Function<R,T> dbFallback,Long time,Long nullTime, TimeUnit unit){
        String key = keyPrefix+id;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(jsonStr)){
            return JSONUtil.toBean(jsonStr, type);
        }
        if ("".equals(jsonStr)){
            return null;
        }
        T t = dbFallback.apply(id);
        if (t==null){
            set(key,"",nullTime,unit);
            return null;
        }
        set(key,JSONUtil.toJsonStr(t),time,unit);
        return t;
    }

    public <T,R> T queryWithLogicalExpire(String keyPrefix, R id,Class<T> type,Function<R,T> dbFallback,Long time,Long nullTime, TimeUnit unit){
        String key =keyPrefix+id;
        //从Redis查询缓存
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        //判断缓存是否命中
        if (StrUtil.isBlank(jsonStr)){
            return null;
        }
        //反序列化对象
        RedisData redisData= JSONUtil.toBean(jsonStr,RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        JSONObject jsonObject = (JSONObject) redisData.getData();
        T t = BeanUtil.toBean(jsonObject, type);
        //判断数据是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //未过期直接返回
            return t;
        }

        String LockKey= LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(LockKey);
        //获取互斥锁
        if (isLock){
            // 二次检查
            jsonStr = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            //反序列化对象
            redisData= JSONUtil.toBean(jsonStr,RedisData.class);
            expireTime = redisData.getExpireTime();
            jsonObject = (JSONObject) redisData.getData();
            t = BeanUtil.toBean(jsonObject, type);
            //判断数据是否过期
            if (expireTime.isAfter(LocalDateTime.now())){
                //未过期直接返回
                return t;
            }
            //确认已过期,开始重建
            //采用线程池的方式,避免重复的创建与销毁浪费资源
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    T t1 = dbFallback.apply(id);
                    setWithLogicalExpire(key,t1,time,unit);
                }catch (Exception e){
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(LockKey);
                }
            });
        }
        //未成功返回旧数据
        return t;
    }

    //预防缓存击穿+缓存穿透
    public <T,R> T queryWithPassThroughAndLogicalExpire(String keyPrefix, R id,Class<T> type,Function<R,T> dbFallback,Long time,Long nullTime, TimeUnit unit){
        String key =keyPrefix+id;
        //从Redis查询缓存
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if ("".equals(jsonStr)){
            return null;
        }
        //反序列化对象
        RedisData redisData= JSONUtil.toBean(jsonStr,RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime==null){
            //判断缓存是否命中
            if (StrUtil.isNotBlank(jsonStr)){
                return JSONUtil.toBean(jsonStr, type);
            }
            T t = dbFallback.apply(id);
            if (t==null){
                set(key,"",nullTime,unit);
                return null;
            }
            set(key,JSONUtil.toJsonStr(t),time,unit);
            return t;
        }
        JSONObject jsonObject = (JSONObject) redisData.getData();
        T t = BeanUtil.toBean(jsonObject, type);
        //判断数据是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //未过期直接返回
            return t;
        }

        String LockKey= LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(LockKey);
        //获取互斥锁
        if (isLock){
            // 二次检查
            jsonStr = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            //反序列化对象
            redisData= JSONUtil.toBean(jsonStr,RedisData.class);
            expireTime = redisData.getExpireTime();
            jsonObject = (JSONObject) redisData.getData();
            t = BeanUtil.toBean(jsonObject, type);
            //判断数据是否过期
            if (expireTime.isAfter(LocalDateTime.now())){
                //未过期直接返回
                return t;
            }
            //确认已过期,开始重建
            //采用线程池的方式,避免重复的创建与销毁浪费资源
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    T t1 = dbFallback.apply(id);
                    setWithLogicalExpire(key,t1,time,unit);
                }catch (Exception e){
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(LockKey);
                }
            });
        }
        //未成功返回旧数据
        return t;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }


}
