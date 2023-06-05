package com.xianyu.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xianyu.dto.Result;
import com.xianyu.entity.Shop;
import com.xianyu.service.IShopService;
import com.xianyu.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;


@RestController
@RequestMapping("/shop")
public class TestController {

    @Resource
    public IShopService shopService;

    /**
     * 根据id查询商铺信息
     *
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @PostMapping ("/test/{id}")
    public Result SaveTest(@PathVariable("id") Long id) {

        return shopService.SaveTest(id);
    }
}
