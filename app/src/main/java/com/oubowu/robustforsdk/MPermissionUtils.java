package com.oubowu.robustforsdk;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

/**
 * Created by Oubowu on 2016/4/19 16:28.
 * 6.0版本请求权限工具类
 */
public class MPermissionUtils {

    private String[] mRequestPermission;

    private MPermissionUtils() {

    }

    private static class Holder {
        static final MPermissionUtils INSTANCE = new MPermissionUtils();
    }

    public static MPermissionUtils getInstance() {
        return Holder.INSTANCE;
    }

    public boolean checkPermission(@NonNull Activity activity, @NonNull int requestCode, @NonNull String... requestPermission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            mRequestPermission = requestPermission;

            boolean granted = true;
            for (String p : requestPermission) {
                final boolean b = ContextCompat
                        .checkSelfPermission(activity, p) == PackageManager.PERMISSION_GRANTED;
                granted &= b;
            }
            if (!granted) {
                ActivityCompat.requestPermissions(activity, requestPermission, requestCode);
            }
            return granted;
        }
        return true;
    }

    public void handlePermissionsResult(int requestCode, String permissions[], int[] grantResults, @NonNull MPermissionsCallBack callBack, @NonNull int... myRequestCode) {
        for (int code : myRequestCode) {
            if (requestCode == code) {
                boolean b = true;
                if (grantResults.length == mRequestPermission.length) {
                    for (int grant : grantResults) {
                        b &= (grant == PackageManager.PERMISSION_GRANTED);
                    }
                } else {
                    b = false;
                }
                if (b) {
                    callBack.onGranted();
                } else {
                    callBack.onDenied();
                }
            }
        }
    }

    public interface MPermissionsCallBack {
        void onGranted();

        void onDenied();
    }

}
