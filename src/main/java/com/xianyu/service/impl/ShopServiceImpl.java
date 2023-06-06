package com.xianyu.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xianyu.dto.Result;
import com.xianyu.entity.Shop;
import com.xianyu.mapper.ShopMapper;
import com.xianyu.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xianyu.utils.CacheClient;
import com.xianyu.utils.RandomUtils;
import com.xianyu.utils.RedisConstants;
import com.xianyu.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.xianyu.utils.RedisConstants.*;



@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

//    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {

        //改用工具类
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, CACHE_NULL_TTL, TimeUnit.MINUTES);
        Shop shop = cacheClient.queryWithPassThroughAndLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById,CACHE_SHOP_TTL, CACHE_SHOP_EXPIRE_TTL,CACHE_NULL_TTL, TimeUnit.MINUTES);
        if (shop==null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * (弃用)使用逻辑过期的方式防止缓存击穿
     * @param id 商铺id
     * @return 商铺实体
     */
//    public Shop queryWithLogicalExpire(Long id){
//        //从Redis查询缓存
//        String jsonStr = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        //判断缓存是否命中
//        if (StrUtil.isBlank(jsonStr)){
//            return null;
//        }
//        //反序列化对象
//        RedisData redisData= JSONUtil.toBean(jsonStr, RedisData.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        JSONObject jsonObject = (JSONObject) redisData.getData();
//        Shop shop = BeanUtil.toBean(jsonObject, Shop.class);
//        //判断数据是否过期
//        if (expireTime.isAfter(LocalDateTime.now())){
//            //未过期直接返回
//            return shop;
//        }
//        String LockKey= LOCK_SHOP_KEY+id;
//        boolean isLock = tryLock(LockKey);
//        //获取互斥锁
//        if (isLock){
//            // 二次检查
//            jsonStr = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//            //反序列化对象
//            redisData= JSONUtil.toBean(jsonStr, RedisData.class);
//            expireTime = redisData.getExpireTime();
//            jsonObject = (JSONObject) redisData.getData();
//            shop = BeanUtil.toBean(jsonObject, Shop.class);
//            //判断数据是否过期
//            if (expireTime.isAfter(LocalDateTime.now())){
//                //未过期直接返回
//                return shop;
//            }
//            //确认已过期,开始重建
//            //采用线程池的方式,避免重复的创建与销毁浪费资源
//            CACHE_REBUILD_EXECUTOR.submit(()->{
//                try {
//                    saveShop2Redis(id,20L);
//                }catch (Exception e){
//                    throw new RuntimeException(e);
//                }
//                finally {
//                    //释放锁
//                    unLock(LockKey);
//                }
//            });
//        }
//        //未成功返回旧数据
//        return shop;
//    }

//    public void saveShop2Redis(Long id,Long expireSecond) throws InterruptedException {
//        //查询店铺数据
//        Shop shop = getById(id);
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSecond));
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(redisData));
//        //模拟缓存的延迟
////        Thread.sleep(200);
//    }

    /**
     * (弃用)互斥锁解决缓存击穿
     * @param id 商铺id
     * @return 商铺实体
     * 改为封装的工具类
     */
//    public Shop queryWithMutex(Long id){
//        String LockKey = LOCK_SHOP_KEY+id;
//        //从Redis查询缓存
//        Map<Object, Object> shopMap = stringRedisTemplate.opsForHash().entries(CACHE_SHOP_KEY + id);
//        //判断缓存是否命中
//        if (!shopMap.isEmpty()){
//            Shop shop = BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
//            if (shop.getId()==null){
//                //查询到空数据
//                return null;
//            }
//            return shop;
//        }
//        try {
//            //获取锁
//            boolean lock = tryLock(LockKey);
//            if (!lock){
//                Thread.sleep(50);
//                queryWithMutex(id);
//            }
//            //获取成功后再次检查缓存是否已存在,做二次检查,如已存在则直接返回
//            //从Redis查询缓存
//            shopMap = stringRedisTemplate.opsForHash().entries(CACHE_SHOP_KEY + id);
//            if (shopMap.size()!=0){
//                return BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
//            }
//            //二次检查还是不存在缓存则重构缓存
//            //查询数据库判断商品是否存在
//            Shop shop = getById(id);
//            //模拟重建延时
////            Thread.sleep(200);
//            if (shop==null){
//                //缓存空数据
//                Map<Object, Object> emptyShopMap = new HashMap<>();
//                emptyShopMap.put("null",null);
//                stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_KEY+id,emptyShopMap);
//                stringRedisTemplate.expire(CACHE_SHOP_KEY+id,CACHE_NULL_TTL+ RandomUtils.getRangeRandom(),TimeUnit.MINUTES);
//                return null;
//            }
//            Map<String, Object> newShopMap = BeanUtil.beanToMap(shop);
//            newShopMap.forEach((key,value)->{
//                if (null!=value){
//                    newShopMap.put(key,value.toString());
//                }
//            });
//            //将查到的信息写入Redis
//            stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_KEY+id,newShopMap);
//            log.info("CACHE_SHOP_TTL:{}",CACHE_SHOP_TTL);
//            stringRedisTemplate.expire(CACHE_SHOP_KEY+id,CACHE_SHOP_TTL+ RandomUtils.getRangeRandom(), TimeUnit.MINUTES);
//            //释放互斥锁
//            unLock(LockKey);
//            //返回商铺信息
//            return shop;
//        } catch (InterruptedException e) {
//            throw  new RuntimeException(e);
//        }finally {
//            unLock(LockKey);
//        }
//    }

    /**
     * (弃用)缓存空对象预防缓存穿透
     * @return 商铺实体
     * 弃用,改为封装的工具类
     */
//    public Shop queryWithPassThrough(Long id){
//        //从Redis查询缓存
//        Map<Object, Object> shopMap = stringRedisTemplate.opsForHash().entries(CACHE_SHOP_KEY + id);
//        //判断缓存是否命中
//        if (!shopMap.isEmpty()){
//            Shop shop = BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
//            if (shop.getId()==null){
//                //查询到空数据
//                return null;
//            }
//            return shop;
//        }
//        //查询数据库判断商品是否存在
//        Shop shop = getById(id);
//        if (shop==null){
//            //缓存空数据
//            Map<Object, Object> emptyShopMap = new HashMap<>();
//            emptyShopMap.put("null",null);
//            stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_KEY+id,emptyShopMap);
//            stringRedisTemplate.expire(CACHE_SHOP_KEY+id,CACHE_NULL_TTL+ RandomUtils.getRangeRandom(),TimeUnit.MINUTES);
//            return null;
//        }
//        Map<String, Object> newShopMap = BeanUtil.beanToMap(shop);
//        newShopMap.forEach((key,value)->{
//            if (null!=value){
//                newShopMap.put(key,value.toString());
//            }
//        });
//        //将查到的信息写入Redis
//        stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_KEY+id,newShopMap);
//        log.info("CACHE_SHOP_TTL:{}",CACHE_SHOP_TTL);
//        stringRedisTemplate.expire(CACHE_SHOP_KEY+id,CACHE_SHOP_TTL+ RandomUtils.getRangeRandom(), TimeUnit.MINUTES);
//        //返回商铺信息
//        return shop;
//    }

//    private boolean tryLock(String key){
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.MINUTES);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    private void unLock(String key){
//        stringRedisTemplate.delete(key);
//    }

    @Transactional
    @Override
    public Result updateShop(Shop shop) {
        if (shop.getId()==null){
            return Result.fail("id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        return Result.ok();
    }

    @Override
    public Result SaveTest(Long id) {
//        try {
//            saveShop2Redis(id,20L);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        return Result.ok();
    }
}
