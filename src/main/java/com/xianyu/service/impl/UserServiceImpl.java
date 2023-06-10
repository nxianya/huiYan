package com.xianyu.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xianyu.dto.LoginFormDTO;
import com.xianyu.dto.Result;
import com.xianyu.dto.UserDTO;
import com.xianyu.entity.User;
import com.xianyu.mapper.UserMapper;
import com.xianyu.service.IUserService;
import com.xianyu.utils.JwtUtils;
import com.xianyu.utils.Md5Util;
import com.xianyu.utils.RegexUtils;
import com.xianyu.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.xianyu.utils.RedisConstants.*;
import static com.xianyu.utils.SystemConstants.*;


@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

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
//        session.setAttribute(phone,code);
        //todo 发送验证码
        //穷,没钱,先模拟
        //使用redis缓存验证码
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL,TimeUnit.MINUTES);
        log.info("code:{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        //校验手机号是否正确
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        //获取输入的验证码进行比较
        String inputCode = loginForm.getCode();
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cacheCode==null&&loginForm.getPassword()==null){
            return Result.fail("请先获取验证码");
        }
        else if(cacheCode!=null){
            if (!inputCode.equals(cacheCode)) {
                return Result.fail("验证码错误");
            }
        }
        User user = query().eq("phone", phone).one();
        if (user==null){
            //注册用户
            user=creatUserWithPhone(phone);
        }else {
            if (!user.getPassword().equals(Md5Util.inputPassToDBPass(loginForm.getPassword()))) {
                return Result.fail("密码错误,请输入正确的密码");
            }
        }
        //将用户的敏感信息筛除
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //userDTO转为HashMap并将userMap中的值转换为String类型,方式一
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName,fieldValue)->
                    fieldValue.toString()
                ));
//        session.setAttribute(DEFAULT_SESSION_KEY, BeanUtil.copyProperties(user, UserDTO.class));
        //生成Token
        String Token = JwtUtils.generateJwt(userMap);
        //方式二
//        userMap.forEach((key,item)->{
//            if (null!=item){
//                userMap.put(key,item.toString());
//            }
//        });


        //将user对象转为Hash存储
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+Token,userMap);

        //设置登录过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY+Token,LOGIN_USER_TTL,TimeUnit.MINUTES);
        //返回Token
        return Result.ok(Token);
    }

    @Override
    public Result logout(HttpServletRequest request) {
        String Token = request.getHeader("authorization");
        stringRedisTemplate.delete(LOGIN_USER_KEY+Token);
        return Result.ok();
    }

    @Override
    public Result queryUser(Long id) {
        User user = getById(id);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
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
