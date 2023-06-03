package com.xianyu.mapper;

import com.xianyu.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.beans.factory.annotation.Value;

@Mapper
public interface UserMapper extends BaseMapper<User> {


}
