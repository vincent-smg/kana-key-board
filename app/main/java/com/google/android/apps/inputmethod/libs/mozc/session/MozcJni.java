package com.google.android.apps.inputmethod.libs.mozc.session;

public final class MozcJni {
    private MozcJni() {}

    public static native boolean initialize();
    public static native byte[] evalCommand(byte[] command);
    public static native boolean onPostLoad(String userProfileDirectoryPath, String dataFilePath);
    public static native String getDataVersion();
}
