package com.xianyu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@MapperScan("com.xianyu.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableTransactionManagement
@SpringBootApplication
public class HyDianPingApplication {

    public static void main(String[] args) {
        SpringApplication.run(HyDianPingApplication.class, args);
    }

}
