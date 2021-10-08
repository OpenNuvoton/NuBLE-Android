package com.example.m310ble.UtilTool;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;


/**
 * Created by ChargeON kawa on 2017/10/5.
 */

public class ResourceManager {
    public static Context context;

    public static final String getHost() {
        return "https://api.stg.chargeon.io";
    }

    public static final String getString(int resId) {
        try{
            return ResourceManager.context.getResources().getString(resId);
        }catch (Resources.NotFoundException e) {

        }
        return null;
    }

    public static final Drawable getDrawable(int resId) {
        Drawable img = ContextCompat.getDrawable(ResourceManager.context, resId);

        return img;
    }

    public static final int getColor(int colorId) {
        return ResourceManager.context.getResources().getColor(colorId);
    }

    public static final String resourcePath() throws NullPointerException {
        Context context = ResourceManager.context;
        if (context == null) {
            throw new NullPointerException("Context is null, please set ResourceManager.context");
        }

        String resourcePath = "android.resource://" + context.getPackageName() + "/";

        return resourcePath;
    }

    public static String getApplicationName() {
        Context context = ResourceManager.context;
        int stringId = context.getApplicationInfo().labelRes;
        return context.getString(stringId);
    }
}
