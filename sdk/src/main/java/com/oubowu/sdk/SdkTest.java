package com.oubowu.sdk;

import android.content.Context;
import android.util.Log;

import com.meituan.robust.Patch;
import com.meituan.robust.PatchExecutor;
import com.meituan.robust.RobustCallBack;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.Logger;
import com.oubowu.sdk.lib.PatchManipulateImp;
import com.oubowu.secret.NdkHelper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Oubowu on 2017/10/9 10:52.
 */
public class SdkTest {

    public static void init(Context context) {

        Logger.clearLogAdapters();
        Logger.addLogAdapter(new AndroidLogAdapter() {
            @Override
            public boolean isLoggable(int priority, String tag) {
                return true;
            }
        });
        Logger.e(BuildConfig.FLAVOR);

        //  创建1个固定线程的线程池，用于串行进行本地补丁的加载和网络请求补丁然后加载的逻辑
        ExecutorService fixedThreadPool = Executors.newFixedThreadPool(1);
        // 读取本地保存的上次加载的补丁的名称
        final String pName = context.getSharedPreferences("com.oubowu.sdk.sp", Context.MODE_PRIVATE).getString("pName", "");
        if (!pName.isEmpty()) {
            // 创建本地补丁加载的线程
            PatchExecutor patchExecutor1 = new PatchExecutor(context.getApplicationContext(), new PatchManipulateImp(true, pName), new RobustCallBack() {
                @Override
                public void onPatchListFetched(boolean result, boolean isNet, List<Patch> patches) {
                    StringBuilder sb = null;
                    if (patches != null) {
                        sb = new StringBuilder(patches.size());
                        for (Patch p : patches) {
                            sb.append(p.getName()).append(",");
                        }
                    }
                    Logger.e("--PatchExecutor", "获取补丁列表后，回调此方法: " + "result=" + result + ";isNet=" + isNet + ";patches=" + (sb == null ? "null" : sb.toString()));
                }

                @Override
                public void onPatchFetched(boolean result, boolean isNet, Patch patch) {
                    Logger.e("--PatchExecutor", "在获取补丁后，回调此方法: " + "result=" + result + ";isNet=" + isNet + ";patch=" + (patch == null ? "null" : patch.getName()));
                }

                @Override
                public void onPatchApplied(boolean result, Patch patch) {
                    Logger.e("--PatchExecutor", "在补丁应用后，回调此方法: " + "result=" + result + ";patch=" + (patch == null ? "null" : patch.getName()));
                }

                @Override
                public void logNotify(String log, String where) {
                    Log.e("App", "RobustCallBack提示log: " + "log=" + log + ";where=" + where);
                }

                @Override
                public void exceptionNotify(Throwable throwable, String where) {
                    Logger.e("--PatchExecutor", "RobustCallBack提示异常: " + "throwable=" + throwable.getMessage() + ";where=" + where);
                }
            });
            // 执行本地补丁加载的线程
            fixedThreadPool.execute(patchExecutor1);
        }

        // 创建网络请求补丁然后加载的线程
        PatchExecutor patchExecutor2 = new PatchExecutor(context.getApplicationContext(), new PatchManipulateImp(false, pName), new RobustCallBack() {
            @Override
            public void onPatchListFetched(boolean result, boolean isNet, List<Patch> patches) {
                StringBuilder sb = null;
                if (patches != null) {
                    sb = new StringBuilder(patches.size());
                    for (Patch p : patches) {
                        sb.append(p.getName()).append(",");
                    }
                }
                Logger.e("PatchExecutor", "获取补丁列表后，回调此方法: " + "result=" + result + ";isNet=" + isNet + ";patches=" + (sb == null ? "null" : sb.toString()));
            }

            @Override
            public void onPatchFetched(boolean result, boolean isNet, Patch patch) {
                Logger.e("PatchExecutor", "在获取补丁后，回调此方法: " + "result=" + result + ";isNet=" + isNet + ";patch=" + (patch == null ? "null" : patch.getName()));
            }

            @Override
            public void onPatchApplied(boolean result, Patch patch) {
                Logger.e("PatchExecutor", "在补丁应用后，回调此方法: " + "result=" + result + ";patch=" + (patch == null ? "null" : patch.getName()));
            }

            @Override
            public void logNotify(String log, String where) {
                Logger.e("App", "RobustCallBack提示log: " + "log=" + log + ";where=" + where);
            }

            @Override
            public void exceptionNotify(Throwable throwable, String where) {
                Logger.e("PatchExecutor", "RobustCallBack提示异常: " + "throwable=" + throwable.getMessage() + ";where=" + where);
            }
        });
        fixedThreadPool.execute(patchExecutor2);
    }

//    @Modify
    public static void callBugMethod(Context context) {

        String strFromCPlus = NdkHelper.getStrFromCPlus();

        Logger.e(strFromCPlus);

//        try {
            int i = Integer.parseInt(strFromCPlus);
//        } catch (NumberFormatException e) {
//            e.printStackTrace();
//            Logger.e("我使用Robust热更新把空指针修复啦！！！");
//        }
//
//        MyClass.call();

    }

//    @Add
//    public static class MyClass {
//        public static void call() {
//            Logger.e("我使用Robust热更新新增了一个静态内部类");
//        }
//    }

}
