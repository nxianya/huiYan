package com.xianyu.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xianyu.dto.LoginFormDTO;
import com.xianyu.dto.Result;
import com.xianyu.entity.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;


public interface IUserService extends IService<User> {

    /**
     * 发送验证码
     * @param phone 手机号
     * @param session session
     * @return 验证码
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 用户登录
     * @param loginForm 登录表单信息
     * @return 登录结果
     */
    Result login(LoginFormDTO loginForm);

    /**
     * @return 退出登录
     */
    Result logout(HttpServletRequest request);

    Result queryUser(Long id);

    Result sign();
}
