package com.xposed.sbclockbegone;

import android.app.usage.UsageEvents;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.TextUtils;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;

public class Xposed implements IXposedHookLoadPackage {

    private static final String TAG = "SBClockBeGone";
    private static final String PACKAGE_NAME = Xposed.class.getPackage().getName();
    public static final String PREFERENCES = "preferences";
    public static final String ACTION_CLOCK_EVENT = "com.xposed.sbclockbegone.action.CLOCK_EVENT";
    public static final String EXTRA_SHOW_CLOCK = "showClock";


    private XSharedPreferences mPrefs;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpParam) throws Throwable {
        mPrefs = new XSharedPreferences(PACKAGE_NAME, PREFERENCES);
        mPrefs.makeWorldReadable();

        String packageName = lpParam.packageName;
        if (packageName.equals("android")) {
            logD("handleLoadPackage: Android");

            try {
                hookUsageStatsService(lpParam);
            } catch (Exception e) {
                logE("Error hooking UsageStatsService, " + e.getMessage());
            }

        } else if (packageName.equals("com.android.systemui")) {
            logD("handleLoadPackage: SystemUI");

            if (isPureNexus()) {
                logD("PureNexus ROM: hooking Clock");
                try {
                    hookStatusBarClock(lpParam);
                } catch (Exception e) {
                    logE("Error hooking Clock, " + e.getMessage());
                }
            } else if (isCM13()) {
                logD("CM13 ROM: hooking ClockController");
                try {
                    hookStatusBarClockController(lpParam);
                } catch (Exception e) {
                    logE("Error hooking ClockController, " + e.getMessage());
                }
            } else {
                logE("ROM not supported");
            }

        }
    }

    private void hookUsageStatsService(XC_LoadPackage.LoadPackageParam lpParam) throws Exception {
        Class UsageStatsServiceClass = findClass("com.android.server.usage.UsageStatsService", lpParam.classLoader);

        findAndHookMethod(UsageStatsServiceClass, "reportEvent", UsageEvents.Event.class, Integer.TYPE, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                UsageEvents.Event event = (UsageEvents.Event) param.args[0];
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    mPrefs.reload();
                    Set<String> blacklist = mPrefs.getStringSet("blacklist", new HashSet<String>(0));
                    String eventPackageName = event.getPackageName();
                    boolean hideClock = blacklist.contains(eventPackageName);

                    Intent intent = new Intent(ACTION_CLOCK_EVENT);
                    intent.putExtra(EXTRA_SHOW_CLOCK, !hideClock);
                    Method getContext = param.thisObject.getClass().getMethod("getContext");
                    Context context = (Context) getContext.invoke(param.thisObject);
                    context.sendBroadcast(intent);
                }
            }
        });
    }

    private void hookStatusBarClock(XC_LoadPackage.LoadPackageParam lpParam) throws Exception {
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
                ((TextView) clockObject).getContext().registerReceiver(eventReceiver, new IntentFilter(ACTION_CLOCK_EVENT));
                setAdditionalInstanceField(clockObject, "eventReceiver", eventReceiver);
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
    }

    private void hookStatusBarClockController(XC_LoadPackage.LoadPackageParam lpParam) throws Exception {
        Class ClockControllerClass = findClass("com.android.systemui.statusbar.phone.ClockController", lpParam.classLoader);

        hookAllConstructors(ClockControllerClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final Object clockControllerObject = param.thisObject;
                BroadcastReceiver eventReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (ACTION_CLOCK_EVENT.equals(action)) {
                            boolean showClock = intent.getBooleanExtra(EXTRA_SHOW_CLOCK, true);
                            callMethod(clockControllerObject, "setVisibility", showClock);
                        }
                    }
                };
                Context context = (Context) getObjectField(clockControllerObject, "mContext");
                context.registerReceiver(eventReceiver, new IntentFilter(ACTION_CLOCK_EVENT));
                setAdditionalInstanceField(clockControllerObject, "eventReceiver", eventReceiver);
            }
        });
        findAndHookMethod(ClockControllerClass, "cleanup", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object clockControllerObject = param.thisObject;
                Object receiverObject = getAdditionalInstanceField(clockControllerObject, "eventReceiver");
                if (null != receiverObject && receiverObject instanceof BroadcastReceiver) {
                    BroadcastReceiver eventReceiver = (BroadcastReceiver) receiverObject;
                    Context context = (Context) getObjectField(clockControllerObject, "mContext");
                    context.unregisterReceiver(eventReceiver);
                }
            }
        });
    }

    private boolean isPureNexus() {
        String pnVersion = getProperty("ro.purenexus.version");
        return !TextUtils.isEmpty(pnVersion);
    }

    private boolean isCM13() {
        String cmVersion = getProperty("ro.cm.version");
        return !TextUtils.isEmpty(cmVersion) && cmVersion.startsWith("13");
    }

    @SuppressWarnings("unchecked")
    private String getProperty(String name) {
        String prop = null;
        try {
            Class systemProps = Class.forName("android.os.SystemProperties");
            if (null != systemProps) {
                Method getProperty = systemProps.getMethod("get", String.class);
                if (null != getProperty) {
                    prop = (String) getProperty.invoke(null, name);
                }
            }
        } catch (Exception e) {
            logE("Error reading SystemProperty \"" + name + "\", " + e.getMessage());
        }
        return prop;
    }

    private void logD(String s) {
        if (BuildConfig.DEBUG)
            XposedBridge.log(String.format(TAG + ": D: %s", s));
    }

    private void logE(String s) {
        XposedBridge.log(String.format(TAG + ": E: %s", s));
    }

}
