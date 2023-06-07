package com.xianyu.utils;

import java.util.concurrent.TimeUnit;

public interface ILock {
    /**
     * 获取锁
     * @param timeout 超时时间
     * @return true:获锁成功,false:失败
     */
    boolean tryLock(long timeout, TimeUnit unit);

    /**
     * 释放锁
     */
    void unLock();
}
