package com.xianyu;

import com.xianyu.utils.JwtUtils;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest
class HmDianPingApplicationTests {

    @Test
    void Test() throws InterruptedException {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id",1);
        claims.put("name","我是一个jwt令牌");
        String jwt = Jwts.builder()
                .signWith(SignatureAlgorithm.HS256, "xianyu")//签名算法
                .setClaims(claims)//自定义内容(负载)
                .setExpiration(new Date(System.currentTimeMillis() + 1))//设置有效期为1h
                .compact();
        System.out.println(jwt);
        Thread.sleep(1000);
        try {
            Map<String, Object> claims1 = JwtUtils.parseJWT(jwt);
            System.out.println(claims1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
