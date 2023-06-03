package com.xianyu.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xianyu.dto.LoginFormDTO;
import com.xianyu.dto.Result;
import com.xianyu.entity.User;
import com.xianyu.mapper.UserMapper;
import com.xianyu.service.IUserService;
import com.xianyu.utils.Md5Util;
import com.xianyu.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.xianyu.utils.SystemConstants.DEFAULT_PASSWORD;
import static com.xianyu.utils.SystemConstants.USER_NICK_NAME_PREFIX;


@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {



    @Override
    public Result sendCode(String phone, HttpSession session) {
        //验证手机号
        if (RegexUtils.isPhoneInvalid(phone)){
            //手机号无效
            return Result.fail("手机号格式错误");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        //存入session
        session.setAttribute(phone,code);
        //todo 发送验证码
        log.info("code:{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号是否正确
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        //获取输入的验证码进行比较
        String inputCode = loginForm.getCode();
        Object cacheCode = session.getAttribute(phone);
        if (cacheCode==null){
            return Result.fail("请先获取验证码");
        }
        else if(!inputCode.equals(cacheCode)){
            return Result.fail("验证码错误");
        }

        User user = query().ge("phone", phone).one();
        if (user==null){
            user=creatUserWithPhone(phone);
        }
        session.setAttribute("user",user);
        return Result.ok();
    }

    private User creatUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        //默认密码
        user.setPassword(DEFAULT_PASSWORD);
        //随机用户名
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        //创建日期和更新日期自动填充
        save(user);
        return user;
    }
}
