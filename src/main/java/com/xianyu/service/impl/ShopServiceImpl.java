package com.xianyu.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.xianyu.dto.Result;
import com.xianyu.entity.Shop;
import com.xianyu.mapper.ShopMapper;
import com.xianyu.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.xianyu.utils.RedisConstants.*;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        //从Redis查询缓存
        Map<Object, Object> shopMap = stringRedisTemplate.opsForHash().entries(CACHE_SHOP_KEY + id);
        //判断缓存是否命中
        if (!shopMap.isEmpty()){
            Shop shop = BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
            if (shop.getId()==null){
                //查询到空数据
                return Result.fail("店铺不存在");
            }
            return Result.ok(shop);
        }
        //查询数据库判断商品是否存在
        Shop shop = getById(id);
        if (shop==null){
            //缓存空数据
            Map<Object, Object> emptyShopMap = new HashMap<>();
            emptyShopMap.put("null",null);
            stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_KEY+id,emptyShopMap);
            stringRedisTemplate.expire(CACHE_SHOP_KEY+id,CACHE_NULL_TTL,TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }
        Map<String, Object> newShopMap = BeanUtil.beanToMap(shop);
        newShopMap.forEach((key,value)->{
            if (null!=value){
                newShopMap.put(key,value.toString());
            }
        });
        //将查到的信息写入Redis
        stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_KEY+id,newShopMap);
        stringRedisTemplate.expire(CACHE_SHOP_KEY+id,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回商铺信息
        return Result.ok(shop);
    }

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
}
