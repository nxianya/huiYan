package com.xianyu.service;

import com.xianyu.dto.Result;
import com.xianyu.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IShopTypeService extends IService<ShopType> {

    Result queryTypeList();
}
