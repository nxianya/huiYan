package com.xianyu.service.impl;

import com.xianyu.entity.Follow;
import com.xianyu.mapper.FollowMapper;
import com.xianyu.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

}
