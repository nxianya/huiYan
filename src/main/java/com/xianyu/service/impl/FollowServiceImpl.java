package com.xianyu.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xianyu.dto.Result;
import com.xianyu.dto.UserDTO;
import com.xianyu.entity.Follow;
import com.xianyu.mapper.FollowMapper;
import com.xianyu.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xianyu.service.IUserService;
import com.xianyu.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.xianyu.utils.RedisConstants.FOLLOW_COMMENT;


@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        if (isFollow) {
            //关注,添加数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(FOLLOW_COMMENT + userId, followId.toString());
            }
        } else {
            //取关,删除数据
            LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, followId);
            boolean isSuccess = remove(queryWrapper);
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(FOLLOW_COMMENT + userId, followId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followId) {
        Long userId = UserHolder.getUser().getId();
        //查询关注状态
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        //获取当前用户和关注用户的交集求得共同关注
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(FOLLOW_COMMENT + userId, FOLLOW_COMMENT + id);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //解析用户信息,去除敏感信息
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOList = userService.listByIds(ids)
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOList);
    }
}
