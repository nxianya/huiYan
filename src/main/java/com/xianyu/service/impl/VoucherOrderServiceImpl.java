package com.xianyu.service.impl;

import com.xianyu.dto.Result;
import com.xianyu.entity.SeckillVoucher;
import com.xianyu.entity.VoucherOrder;
import com.xianyu.mapper.VoucherOrderMapper;
import com.xianyu.service.ISeckillVoucherService;
import com.xianyu.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xianyu.utils.RedisIdWorker;
import com.xianyu.utils.SimpleRedisLock;
import com.xianyu.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static com.xianyu.utils.RedisConstants.*;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;


    @Autowired
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {

        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //针对同一用户产生锁对象
        Long userId = UserHolder.getUser().getId();
//        SimpleRedisLock simpleRedisLock = new SimpleRedisLock(VOUCHER_SECKILL_USER+userId, userId, stringRedisTemplate);
        //解决方式:使用redis里的事务(通过Lua脚本实现)
        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("VOUCHER_SECKILL_USER", userId, stringRedisTemplate);
        try {

        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始!");
        }

        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀活动已结束!");
        }
        if (seckillVoucher.getStock()<1){
            return Result.fail("秒杀卷已抢完!");
        }

            //方式一:缓存信息至活动结束,减少反复点击购票导致数据库的负担增加,但也消耗了内存
//            boolean lock = simpleRedisLock.tryLock(LocalDateTime.now().until(seckillVoucher.getEndTime(), ChronoUnit.MINUTES), TimeUnit.MINUTES);
            //方式二:共用一把锁,锁的存在时间较短,业务结束主动释放,减少了Redis内存的消耗,但也会因业务阻塞而锁超时导致锁的误删和数据库的负担增加
            boolean lock = simpleRedisLock.tryLock(10L, TimeUnit.SECONDS);
            if ( !lock){
                    return Result.fail("请勿重复下单!");
                }
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count>0){
                return Result.fail("请勿重复下单!");
            }
            //二次查询确保库存大于0后扣减库存
            boolean flag = seckillVoucherService.update().setSql("stock=stock-1").eq("voucher_id", voucherId)
                    .gt("stock",0).update();
            if (!flag){
                return Result.fail("库存不足!");
            }

            //创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            long orderId = redisIdWorker.nextId(SECKILL_STOCK_KEY);
            voucherOrder.setVoucherId(voucherId);
            voucherOrder.setUserId(userId);
            voucherOrder.setId(orderId);
            save(voucherOrder);
            return Result.ok(orderId);
        } finally {
            /**
             * 方式一:活动过期自动释放用户再次购票的锁
             */
//            if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
//                simpleRedisLock.unLock();
//            }
            if (simpleRedisLock.getValue()==userId){
                simpleRedisLock.unLock();
            }
        }
    }
}
