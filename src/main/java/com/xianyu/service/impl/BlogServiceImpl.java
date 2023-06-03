package com.xianyu.service.impl;

import com.xianyu.entity.Blog;
import com.xianyu.mapper.BlogMapper;
import com.xianyu.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

}
