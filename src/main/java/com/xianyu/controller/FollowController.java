package com.xianyu.controller;


import com.xianyu.dto.Result;
import com.xianyu.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;


@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;

    /**
     * 关注/取关接口
     * @param followId 关注用户的id
     * @param isFollow 关注/取关
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followId,@PathVariable("isFollow") Boolean isFollow){
        return followService.follow(followId,isFollow);
    }

    /**
     * 判断是否关注
     * @param followId 关注用户的id
     * @return 关注/取关
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followId){
        return followService.isFollow(followId);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id){
        return followService.followCommons(id);
    }
}
