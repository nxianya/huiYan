package com.xianyu.utils;

import org.apache.commons.codec.digest.DigestUtils;

public class Md5Util {
    public static String md5(String src){
        return DigestUtils.md5Hex(src);
    }

    private static final String salt ="xianyu";

    private static String inputPassToFormPass(String inputPass){
        String str ="" + salt.charAt(1) + salt.charAt(3) + inputPass + salt.charAt(1) + salt.charAt(4);
        return md5(str);
    }

    private static String FormPassToDBPass(String formPass,String salt){
        String str = salt.charAt(1) + salt.charAt(3) + formPass + salt.charAt(1) + salt.charAt(4);
        return md5(str);
    }

    public static String inputPassToDBPass(String inputPass){
        String fromPass = inputPassToFormPass(inputPass);
        String dbPass = FormPassToDBPass(fromPass, salt);
        return dbPass;
    }

}
