package com.xianyu.service;

import com.xianyu.dto.Result;
import com.xianyu.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result updateShop(Shop shop);

    Result SaveTest(Long id);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
