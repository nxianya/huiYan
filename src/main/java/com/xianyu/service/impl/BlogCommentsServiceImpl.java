package com.xianyu.service.impl;

import com.xianyu.entity.BlogComments;
import com.xianyu.mapper.BlogCommentsMapper;
import com.xianyu.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
