package com.xposed.sbclockbegone;

import android.app.usage.UsageEvents;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;

public class Xposed implements IXposedHookLoadPackage {

    private static final String TAG = "SBClockBeGone";
    private static final String PACKAGE_NAME = Xposed.class.getPackage().getName();
    public static final String ACTION_CLOCK_EVENT = "com.xposed.sbclockbegone.action.CLOCK_EVENT";
    public static final String EXTRA_SHOW_CLOCK = "showClock";

    public static final String PREFERENCES = "preferences";

    private XSharedPreferences mPrefs;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpParam) throws Throwable {
        mPrefs = new XSharedPreferences(PACKAGE_NAME, PREFERENCES);
        mPrefs.makeWorldReadable();

        String packageName = lpParam.packageName;
        if (packageName.equals("android")) {
            Log.d(TAG, "handleLoadPackage: Android");

            try {
                Class UsageStatsServiceClass = findClass("com.android.server.usage.UsageStatsService", lpParam.classLoader);

                findAndHookMethod(UsageStatsServiceClass, "reportEvent", UsageEvents.Event.class, Integer.TYPE, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        UsageEvents.Event event = (UsageEvents.Event) param.args[0];
                        if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                            String eventPackageName = event.getPackageName();

                            mPrefs.reload();
                            Set<String> blacklist = mPrefs.getStringSet("blacklist", new HashSet<String>(0));
                            boolean hideClock = blacklist.contains(eventPackageName);

                            Intent intent = new Intent(ACTION_CLOCK_EVENT);
                            intent.putExtra(EXTRA_SHOW_CLOCK, !hideClock);
                            Method getContext = param.thisObject.getClass().getMethod("getContext");
                            Context context = (Context) getContext.invoke(param.thisObject);
                            context.sendBroadcast(intent);
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "handleLoadPackage: error hooking UsageStatsService.reportEvent, " + e.getMessage());
            }
        } else if (packageName.equals("com.android.systemui")) {
            Log.d(TAG, "handleLoadPackage: SystemUI");

            try {
                Class ClockClass = findClass("com.android.systemui.statusbar.policy.Clock", lpParam.classLoader);

                findAndHookMethod(ClockClass, "onAttachedToWindow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        final Object clockObject = param.thisObject;
                        BroadcastReceiver eventReceiver = new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                String action = intent.getAction();
                                if (ACTION_CLOCK_EVENT.equals(action)) {
                                    boolean showClock = intent.getBooleanExtra(EXTRA_SHOW_CLOCK, true);
                                    XposedHelpers.setBooleanField(clockObject, "mShowClock", showClock);
                                    callMethod(clockObject, "updateClockVisibility");
                                }
                            }
                        };
                        setAdditionalInstanceField(clockObject, "eventReceiver", eventReceiver);
                        ((TextView) clockObject).getContext().registerReceiver(eventReceiver, new IntentFilter(ACTION_CLOCK_EVENT));
                    }
                });
                findAndHookMethod(ClockClass, "onDetachedFromWindow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object clockObject = param.thisObject;
                        Object receiverObject = getAdditionalInstanceField(clockObject, "eventReceiver");
                        if (null != receiverObject && receiverObject instanceof BroadcastReceiver) {
                            BroadcastReceiver eventReceiver = (BroadcastReceiver) receiverObject;
                            ((TextView) clockObject).getContext().unregisterReceiver(eventReceiver);
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "handleLoadPackage: error hooking Clock.updateClock, " + e.getMessage());
            }
        }
    }

}
