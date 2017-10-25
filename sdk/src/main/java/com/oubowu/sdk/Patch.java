package com.oubowu.sdk;

import cn.bmob.v3.BmobObject;
import cn.bmob.v3.datatype.BmobFile;

/**
 * Created by Oubowu on 2017/10/10 3:11.
 */
public class Patch extends BmobObject {

    private String sdkVersion;
    private String patchVersion;
    private BmobFile patchUrl;

    public String getSdkVersion() {
        return sdkVersion;
    }

    public void setSdkVersion(String sdkVersion) {
        this.sdkVersion = sdkVersion;
    }

    public String getPatchVersion() {
        return patchVersion;
    }

    public void setPatchVersion(String patchVersion) {
        this.patchVersion = patchVersion;
    }

    public BmobFile getPatchUrl() {
        return patchUrl;
    }

    public void setPatchUrl(BmobFile patchUrl) {
        this.patchUrl = patchUrl;
    }

    @Override
    public String toString() {
        return "Patch{" + "sdkVersion='" + sdkVersion + '\'' + ", patchVersion='" + patchVersion + '\'' + ", patchUrl=" + patchUrl.getFileUrl() + "} " + super.toString();
    }
}
