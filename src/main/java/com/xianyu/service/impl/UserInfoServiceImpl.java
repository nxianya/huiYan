package com.xianyu.service.impl;

import com.xianyu.entity.UserInfo;
import com.xianyu.mapper.UserInfoMapper;
import com.xianyu.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
