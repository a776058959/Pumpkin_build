package com.pumpkin.server;

import android.content.Context;
import android.content.SharedPreferences;

/** 极简偏好存储。 */
public final class Prefs {

    private static final String NAME = "pumpkin_shell";

    private Prefs() {
    }

    private static SharedPreferences sp(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String get(Context ctx, String key, String def) {
        return sp(ctx).getString(key, def);
    }

    public static void put(Context ctx, String key, String value) {
        sp(ctx).edit().putString(key, value).apply();
    }

    public static boolean getBool(Context ctx, String key, boolean def) {
        return sp(ctx).getBoolean(key, def);
    }

    public static void putBool(Context ctx, String key, boolean value) {
        sp(ctx).edit().putBoolean(key, value).apply();
    }

    public static int getInt(Context ctx, String key, int def) {
        return sp(ctx).getInt(key, def);
    }

    public static void putInt(Context ctx, String key, int value) {
        sp(ctx).edit().putInt(key, value).apply();
    }
}
