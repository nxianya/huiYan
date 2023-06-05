package com.xianyu.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.xianyu.dto.Result;
import com.xianyu.entity.ShopType;
import com.xianyu.mapper.ShopTypeMapper;
import com.xianyu.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.xianyu.utils.RedisConstants.CACHE_SHOP_LIST_KEY;
import static com.xianyu.utils.RedisConstants.CACHE_SHOP_LIST_TTL;


@Slf4j
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        //从Redis获取缓存
        String typeStr = stringRedisTemplate.opsForValue().get(CACHE_SHOP_LIST_KEY);
        if (StringUtils.isNotBlank(typeStr)){
                List<ShopType> shopTypes = JSONUtil.toList(typeStr, ShopType.class);
//                log.info("{}",shopTypes);
                return Result.ok(shopTypes);
        }
        //未命中,从数据库查询
        List<ShopType> typeList =query().orderByAsc("sort").list();
        //缓存店铺分类信息
        String jsonString = JSONUtil.toJsonStr(typeList);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_LIST_KEY, jsonString,CACHE_SHOP_LIST_TTL, TimeUnit.MINUTES);
        return Result.ok(typeList);
    }
}
