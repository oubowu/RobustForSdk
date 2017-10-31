package com.oubowu.robustforsdk;

import android.Manifest;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.oubowu.sdk.SdkTest;
import com.oubowu.secret.NdkHelper;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int REQUEST_PERMISSION_SUCCESS = 111;

    private TextView mTvHello;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTvHello = (TextView) findViewById(R.id.tv_hello);

        findViewById(R.id.bt_sdk).setOnClickListener(this);

        checkPermissionAndCallSdk();

    }

    private void checkPermissionAndCallSdk() {
        boolean checkPermission = MPermissionUtils.getInstance()
                .checkPermission(this, REQUEST_PERMISSION_SUCCESS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE);
        if (checkPermission) {
            SdkTest.init(this);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bt_sdk:
                SdkTest.callBugMethod(this);
                mTvHello.setText(NdkHelper.getStrFromCPlus());
                break;
            default:
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        MPermissionUtils.getInstance().handlePermissionsResult(requestCode, permissions, grantResults, new MPermissionUtils.MPermissionsCallBack() {
            @Override
            public void onGranted() {
                SdkTest.init(MainActivity.this);
            }

            @Override
            public void onDenied() {
                Toast.makeText(MainActivity.this, "未授予相应的权限，SDK功能受到限制。", Toast.LENGTH_SHORT).show();
            }
        }, REQUEST_PERMISSION_SUCCESS);
    }

}
