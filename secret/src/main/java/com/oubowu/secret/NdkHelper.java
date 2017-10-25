package com.oubowu.secret;

import android.content.Context;

/**
 * Created by Oubowu on 2017/10/11 9:46.
 */
public class NdkHelper {

    static {
        System.loadLibrary("secret-lib");
    }

    // 加载动态连接库的文件所在的包名必须和so库的包名一致
    public static native String getStrFromCPlus();

    /**
     * AES加密
     *
     * @param sourceFilePath 需要加密的文件路径
     * @param destFilePath   生成加密后的文件路径
     * @return 加密成功与否的信息
     */
    public static native String e(String sourceFilePath, String destFilePath);

    /**
     * AES 解密
     *
     * @param sourceFilePath 需要解密的文件路径
     * @param destFilePath   生成解密后的文件路径
     * @return 解密成功与否的信息
     */
    public static native String d(String sourceFilePath, String destFilePath);

    public static native String p(Context ctx,String pName,boolean e);

}
