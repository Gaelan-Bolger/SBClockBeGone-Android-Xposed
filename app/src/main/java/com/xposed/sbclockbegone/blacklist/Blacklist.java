package com.xposed.sbclockbegone.blacklist;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.xposed.sbclockbegone.Xposed;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class Blacklist {

    @NonNull
    public static List<String> get(Context context) {
        List<String> list = new ArrayList<>();
        SharedPreferences prefs = getSharedPreferences(context);
        list.addAll(prefs.getStringSet("blacklist", new HashSet<String>(0)));
        return list;
    }

    public static void set(Context context, @Nullable List<String> packageNames) {
        SharedPreferences prefs = getSharedPreferences(context);
        SharedPreferences.Editor edit = prefs.edit();
        if (null != packageNames && !packageNames.isEmpty())
            edit.putStringSet("blacklist", new HashSet<>(packageNames));
        else
            edit.putStringSet("blacklist", null);
        edit.apply();
    }

    public static boolean contains(Context context, String packageName) {
        List<String> list = get(context);
        return list.contains(packageName);
    }

    public static void add(Context context, String packageName) {
        List<String> list = get(context);
        if (!list.contains(packageName)) {
            list.add(packageName);
            set(context, list);
        }
    }

    public static void remove(Context context, String packageName) {
        List<String> list = get(context);
        if (list.contains(packageName)) {
            list.remove(packageName);
            set(context, list);
        }
    }

    @SuppressWarnings("deprecation")
    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(Xposed.PREFERENCES, Context.MODE_WORLD_READABLE);
    }
}
