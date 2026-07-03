/*
 * Copyright 2026 Nuvoton Technology Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
