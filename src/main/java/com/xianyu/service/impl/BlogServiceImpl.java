package com.xianyu.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xianyu.dto.Result;
import com.xianyu.dto.ScrollResult;
import com.xianyu.dto.UserDTO;
import com.xianyu.entity.Blog;
import com.xianyu.entity.Follow;
import com.xianyu.entity.User;
import com.xianyu.mapper.BlogMapper;
import com.xianyu.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xianyu.service.IFollowService;
import com.xianyu.service.IUserService;
import com.xianyu.utils.SystemConstants;
import com.xianyu.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.xianyu.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.xianyu.utils.RedisConstants.FEED_KEY;


@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        //判断当前用户是否已点赞
        if (user == null) {
            return;
        }
        Long userId = user.getId();
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + blog.getId(), userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query().orderByDesc("liked").page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //判断当前用户是否已点赞
        Long userId = UserHolder.getUser().getId();
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userId.toString());
        if (score != null) {
            //取消点赞
            boolean isSuccess = update().setSql("liked=liked-1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + id, userId.toString());
                return Result.ok("点赞已取消");
            }
        }
        //保存到sortedSet集合
        boolean isSuccess = update().setSql("liked=liked+1").eq("id", id).update();
        if (isSuccess) {
            stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id, userId.toString(), System.currentTimeMillis());
            return Result.ok("点赞成功");
        }
        return Result.fail("网络异常,请刷新重试");
    }

    @Override
    public Result queryBlogLikes(Long id) {
        Set<String> range = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0L, 4L);
        if (range == null || range.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = range.stream().map(Long::valueOf).collect(Collectors.toList());
        String idsStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOList = userService.query().in("id", ids).last("ORDER BY FIELD(id," + idsStr + ")").list().stream().map(item -> BeanUtil.copyProperties(item, UserDTO.class)).collect(Collectors.toList());
//        range.toArray()
        return Result.ok(userDTOList);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("笔记新增失败,请刷新重试");
        }
        //查询博主的粉丝,进行推送
        LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Follow::getFollowUserId, user.getId());
        List<Long> ids = followService.list(queryWrapper).stream().map(Follow::getUserId).collect(Collectors.toList());
        ids.forEach(id -> stringRedisTemplate.opsForZSet().add(FEED_KEY + id, blog.getId().toString(), System.currentTimeMillis()));
        // 返回id
        return Result.ok();
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //获取当前用户的收件箱内容
        Long userId = UserHolder.getUser().getId();
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(
                FEED_KEY + userId, 0, max, offset, 3
        );
        //解析数据
        if (typedTuples==null||typedTuples.isEmpty()){
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime=0;
        int count=1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //获取Id
            ids.add(Long.valueOf(typedTuple.getValue()));
            //获取分数(时间戳)
            long timeStamp = typedTuple.getScore().longValue();
            if (timeStamp==minTime){
                count+=count;
            }else {
                minTime=timeStamp;
                count=1;
            }
        }
        String idsStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id",ids).last("ORDER BY FIELD(id," + idsStr + ")").list();
        for (Blog blog : blogs) {
            //完善笔记发布者的相关信息
            queryBlogUser(blog);
            //是否被当前用户点赞
            isBlogLiked(blog);
        }
        //封装并返回
        ScrollResult cr = new ScrollResult();
        cr.setList(blogs);
        cr.setMinTime(minTime);
        cr.setOffset(count);
        return Result.ok(cr);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
