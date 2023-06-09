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
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
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
        if (RegexUtils.isPhoneInvalid(phone)) {
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
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.info("code:{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        //校验手机号是否正确
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //获取输入的验证码进行比较
        String inputCode = loginForm.getCode();
        String inputPassword = loginForm.getPassword();
        if (inputCode == null && inputPassword == null) {
            return Result.fail("请输入验证码或密码");
        }else if (inputCode != null && inputPassword != null){
            //虽然不可能但还是要考虑到
            return Result.fail("无效输入");
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        //短信登录
        if (inputCode != null ) {
            if (cacheCode==null){
                return Result.fail("请先获取验证码");
            }
            else if (!inputCode.equals(cacheCode)) {
                return Result.fail("验证码错误");
            }
        }
        //密码登录
        User user = query().eq("phone", phone).one();
        if (inputPassword != null ) {
            if (!user.getPassword().equals(Md5Util.inputPassToDBPass(loginForm.getPassword()))) {
                return Result.fail("密码错误");
            }
        }
        if (user == null) {
            //注册用户
            user = creatUserWithPhone(phone);
        }
        //将用户的敏感信息筛除
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //userDTO转为HashMap并将userMap中的值转换为String类型,方式一
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) ->
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
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + Token, userMap);

        //设置登录过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY + Token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //返回Token
        return Result.ok(Token);
    }

    @Override
    public Result logout(HttpServletRequest request) {
        String Token = request.getHeader("authorization");
        stringRedisTemplate.delete(LOGIN_USER_KEY + Token);
        return Result.ok();
    }

    @Override
    public Result queryUser(Long id) {
        User user = getById(id);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    @Override
    public Result sign() {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //获取当前日期
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyy/MM"));
        //拼接组装key
        String key = USER_SIGN_KEY+userId+keySuffix;
        //获取当前天数
        int offset = now.getDayOfMonth();
        Boolean result = stringRedisTemplate.opsForValue().getBit(key, offset - 1);
        if (Boolean.TRUE.equals(result)) {
            return Result.fail("今日已签到");
        }
        //存入redis
        stringRedisTemplate.opsForValue().setBit(key,offset-1,true);
        return Result.ok("签到成功");
    }

    private User creatUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        //默认密码
        user.setPassword(DEFAULT_PASSWORD);
        //随机用户名
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //创建日期和更新日期自动填充
        save(user);
        return user;
    }


    @Override
    public Result signCount() {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //获取当前时间
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyy/MM"));
        //组装key
        String key = USER_SIGN_KEY+userId+keySuffix;
        //获取截止本日的签到记录
        int dayOfMonth = now.getDayOfMonth();
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result==null||result.isEmpty()){
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num==null||num==0){
            return Result.ok(0);
        }
        //计数器
        int count=0;
        //循环遍历位移解析签到记录
        while (true){
            if ((num & 1)==0){
                //遇0:未签到,退出循环
                break;
            }else {
                //遇1:计数器加一
                count++;
            }
            //向右位移遍历
            num >>>=1;
        }
        return Result.ok(count);
    }
}
