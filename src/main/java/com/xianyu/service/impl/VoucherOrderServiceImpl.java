package com.xianyu.service.impl;

import com.xianyu.dto.Result;
import com.xianyu.entity.SeckillVoucher;
import com.xianyu.entity.VoucherOrder;
import com.xianyu.mapper.VoucherOrderMapper;
import com.xianyu.service.ISeckillVoucherService;
import com.xianyu.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xianyu.utils.CacheClient;
import com.xianyu.utils.RedisIdWorker;
import com.xianyu.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.xianyu.utils.RedisConstants.*;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private CacheClient cacheClient;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始!");
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀活动已结束!");
        }
        if (seckillVoucher.getStock()<1){
            return Result.fail("秒杀卷已抢完!");
        }
        //扣减库存
        boolean flag = seckillVoucherService.update().setSql("stock=stock-1").eq("voucher_id", voucherId).update();
        if (!flag){
            return Result.fail("库存不足!");
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId(SECKILL_STOCK_KEY);
        Long userId = UserHolder.getUser().getId();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        voucherOrder.setId(orderId);
        save(voucherOrder);
        return Result.ok(voucherOrder);
    }
}
