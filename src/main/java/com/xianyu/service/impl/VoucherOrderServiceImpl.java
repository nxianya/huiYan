package com.xianyu.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.xianyu.dto.Result;
import com.xianyu.entity.VoucherOrder;
import com.xianyu.mapper.VoucherOrderMapper;
import com.xianyu.service.ISeckillVoucherService;
import com.xianyu.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xianyu.utils.RedisIdWorker;
import com.xianyu.utils.UserHolder;
import lombok.SneakyThrows;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.xianyu.utils.RedisConstants.*;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;


    @Autowired
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final ExecutorService SECKLII_ORDER_EXECUTOR= Executors.newSingleThreadExecutor();
    private class voucherOrderHandler implements Runnable{
        String queueName ="stream.orders";
        @Override
        public void run() {
            while (true){
                try {
                    //获取消息队列中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    if (list==null||list.isEmpty()){
                        continue;
                    }
                    //解析队列中的信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //创建订单
                    handleVoucherOrder(voucherOrder);
                    //ACK确认 ->SACK命令
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.error("队列处理异常",e);
                    handlePendingList();
                }
            }
        }
        @SneakyThrows
        private void handlePendingList() {
            while (true){
                try {
                    //获取pendingList队列中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    if (list==null||list.isEmpty()){
                        break;
                    }
                    //解析队列中的信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //创建订单
                    handleVoucherOrder(voucherOrder);
                    //ACK确认 ->SACK命令
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.error("处理pending-list时产生的错误日志",e);
                    //防止CPU占用过高的可能性
                    Thread.sleep(100);
                }
            }
        }
    }




    //(弃用)阻塞队列
//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
//    private class voucherOrderHandler implements Runnable{
//
//
//        @Override
//        public void run() {
//            while (true){
//                try {
//                    //获取阻塞队列中的订单信息
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    //创建订单
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("队列处理异常",e);
//                }
//            }
//        }
//    }

    //获取当前类的代理对象
    private IVoucherOrderService proxy;
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        RLock rLock = redissonClient.getLock("lock:order:" + userId);
        boolean lock = rLock.tryLock();
        if ( !lock){
            log.error("获锁失败");
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            rLock.unlock();
        }

    }

    //    @PostConstruct:在本类初始化完毕后执行该方法
    @PostConstruct
    private void init(){
        SECKLII_ORDER_EXECUTOR.submit(new voucherOrderHandler());
    }

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 采用异步通知的方式提高并发能力和吞吐量
     * @param voucherId 秒杀卷id
     * @return 订单id
     */
    public Result seckillVoucher(Long voucherId) {
        long orderId = redisIdWorker.nextId(SECKILL_STOCK_KEY);
        String str = String.valueOf(orderId);
        //1.执行lua脚本
        Long userId = UserHolder.getUser().getId();
        Long getResult = stringRedisTemplate.execute(
                SECKILL_SCRIPT, Collections.emptyList(),
                voucherId.toString(), userId.toString(),str);
        int result = getResult.intValue();
        if (result!=0){
            return Result.fail(result==1?"库存不足":"请勿重复下单!");
        }
        //保存到阻塞队列
        //创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            voucherOrder.setVoucherId(voucherId);
            voucherOrder.setUserId(userId);
            voucherOrder.setId(orderId);

        proxy =(IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }

    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
            if (count>0){
                return;
            }
            //二次查询确保库存大于0后扣减库存
            seckillVoucherService.update().setSql("stock=stock-1").eq("voucher_id", voucherOrder.getVoucherId())
                    .gt("stock",0).update();
            save(voucherOrder);
    }


//    @Override
//    @Transactional
//    public Result seckillVoucher(Long voucherId) {
//
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        //针对同一用户产生锁对象
//        Long userId = UserHolder.getUser().getId();
////        SimpleRedisLock simpleRedisLock = new SimpleRedisLock(VOUCHER_SECKILL_USER+userId, userId, stringRedisTemplate);
//        //解决方式:使用redis里的事务(通过Lua脚本实现)
////        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("VOUCHER_SECKILL_USER",stringRedisTemplate);
//        //使用Redisson提供的可重入锁
//        RLock rLock = redissonClient.getLock("VOUCHER_SECKILL_USER" + userId);
//        try {
//            boolean lock = rLock.tryLock();
//        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始!");
//        }
//
//        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀活动已结束!");
//        }
//        if (seckillVoucher.getStock()<1){
//            return Result.fail("秒杀卷已抢完!");
//        }
//
//            //方式一:缓存信息至活动结束,减少反复点击购票导致数据库的负担增加,但也消耗了内存
////            boolean lock = simpleRedisLock.tryLock(LocalDateTime.now().until(seckillVoucher.getEndTime(), ChronoUnit.MINUTES), TimeUnit.MINUTES);
//            //方式二:共用一把锁,锁的存在时间较短,业务结束主动释放,减少了Redis内存的消耗,但也会因业务阻塞而锁超时导致锁的误删和数据库的负担增加
////            boolean lock = simpleRedisLock.tryLock(10L, TimeUnit.SECONDS);
//            if ( !lock){
//                    return Result.fail("请勿重复下单!");
//                }
//            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//            if (count>0){
//                return Result.fail("请勿重复下单!");
//            }
//            //二次查询确保库存大于0后扣减库存
//            boolean flag = seckillVoucherService.update().setSql("stock=stock-1").eq("voucher_id", voucherId)
//                    .gt("stock",0).update();
//            if (!flag){
//                return Result.fail("库存不足!");
//            }
//
//            //创建订单
//            VoucherOrder voucherOrder = new VoucherOrder();
//            long orderId = redisIdWorker.nextId(SECKILL_STOCK_KEY);
//            voucherOrder.setVoucherId(voucherId);
//            voucherOrder.setUserId(userId);
//            voucherOrder.setId(orderId);
//            save(voucherOrder);
//            return Result.ok(orderId);
//        } finally {
//            /**
//             * 方式一:活动过期自动释放用户再次购票的锁
//             */
//
////            if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
////                simpleRedisLock.unLock();
////            }
////                simpleRedisLock.unLock();
//            rLock.unlock();
//        }
//    }
}
