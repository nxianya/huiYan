package com.xianyu.utils;

public class SystemConstants {
    public static final String IMAGE_UPLOAD_DIR = "E:\\MyProject\\nginx-1.18.0\\html\\hmdp\\imgs\\";
    public static final String USER_NICK_NAME_PREFIX = "user_";
    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;

    public static final String DEFAULT_SESSION_KEY = "user";
    public static final String DEFAULT_PASSWORD=Md5Util.inputPassToDBPass("123456");
}
