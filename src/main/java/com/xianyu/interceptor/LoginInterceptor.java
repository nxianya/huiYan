package com.xianyu.interceptor;

import com.xianyu.dto.UserDTO;
import com.xianyu.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static com.xianyu.utils.SystemConstants.DEFAULT_SESSION_KEY;

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取session中的用户信息
        HttpSession session = request.getSession();
        Object user = session.getAttribute(DEFAULT_SESSION_KEY);
        if (user==null){
            //拦截
            response.setStatus(401);
            return false;
        }
        //将用户信息存入ThreadLocal副本
        UserHolder.saveUser((UserDTO) user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
