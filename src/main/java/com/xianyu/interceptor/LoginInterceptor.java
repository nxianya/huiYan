package com.xianyu.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.xianyu.dto.UserDTO;
import com.xianyu.utils.JwtUtils;
import com.xianyu.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.xianyu.utils.RedisConstants.LOGIN_USER_KEY;
import static com.xianyu.utils.RedisConstants.LOGIN_USER_TTL;
import static com.xianyu.utils.SystemConstants.DEFAULT_SESSION_KEY;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("url:{}",request.getRequestURI());
        //获取请求头中的Token
        String Token = request.getHeader("authorization");
        if (StrUtil.isBlank(Token)){
            response.setStatus(401);
            return false;
        }
        //解析令牌,解析失败则拦截
        try {
            JwtUtils.parseJWT(Token);
        } catch (Exception e) {
            response.setStatus(401);
            return false;
        }
        //从Redis中获取用户信息
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY+Token);
        if (userMap.isEmpty()){
            //拦截
            response.setStatus(401);
            return false;
        }
        //还原Bean
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false);
        //将用户信息存入ThreadLocal副本
        UserHolder.saveUser(userDTO);
        //刷新Token有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY+Token,LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
