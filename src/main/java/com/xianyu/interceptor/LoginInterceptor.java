package com.xianyu.interceptor;

import com.xianyu.utils.JwtUtils;
import com.xianyu.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.xianyu.utils.RedisConstants.LOGIN_USER_KEY;

/**
 * 此拦截器用于对请求做拦截
 */
public class LoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取请求头中的Token
        String Token = request.getHeader("authorization");
        //解析令牌,设定强制过期时间
        try {
            JwtUtils.parseJWT(Token);
        } catch (Exception e) {
            //todo 抛出自定义异常,提示用户
            //令牌被修改或已过期
            stringRedisTemplate.delete(LOGIN_USER_KEY+Token);
            response.setStatus(401);
            return false;
        }
        //从ThreadLocal中获取用户信息
        if (UserHolder.getUser()==null){
            response.setStatus(401);
            return false;
        }
        return true;
    }

}
