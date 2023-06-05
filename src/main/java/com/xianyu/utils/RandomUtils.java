package com.xianyu.utils;

import cn.hutool.core.util.RandomUtil;

public class RandomUtils {
    public static Long getRangeRandom(){
        return RandomUtil.randomLong(0,5);
    }
}
