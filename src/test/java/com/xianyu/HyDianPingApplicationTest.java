package com.xianyu;

import com.xianyu.utils.JwtUtils;
import com.xianyu.utils.RedisIdWorker;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HyDianPingApplicationTest {

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

    @Autowired
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void TestTimeStamp() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task=()->{
            for (int i = 0; i < 100; i++) {
                System.out.println(redisIdWorker.nextId("voucher"));
            }
        };
        long start = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        long end = System.currentTimeMillis();
        latch.await();
        System.out.println(end - start);
    }
}
