package com.xianyu.service;

import com.xianyu.dto.Result;
import com.xianyu.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IFollowService extends IService<Follow> {

    Result follow(Long followId, Boolean isFollow);

    Result isFollow(Long followId);

    Result followCommons(Long id);
}
