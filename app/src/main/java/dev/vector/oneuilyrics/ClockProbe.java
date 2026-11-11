package dev.vector.oneuilyrics;

import android.content.res.Resources;
import android.view.View;
import android.widget.TextView;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Read-only, bounded diagnostics for Samsung's status-bar Clock class. */
public final class ClockProbe implements IXposedHookLoadPackage {
    private static final String TAG = "OneUILyricsProbe: ";
    private static final String TARGET = "com.android.systemui.statusbar.policy.Clock";
    private static final Set<View> SEEN = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private static int constructed = 0;
    private static int attached = 0;
    private static int clockUpdates = 0;
    private static int smallTimeCalls = 0;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        if (!"com.android.systemui".equals(p.packageName)) return;
        // The edgelighting process contains the class but does not host the status bar.
        if (!"com.android.systemui".equals(p.processName)) return;
        if (p.classLoader == null) return;
        try {
            Class<?> type = XposedHelpers.findClassIfExists(TARGET, p.classLoader);
            if (type == null || !View.class.isAssignableFrom(type)) {
                log("clock class absent in loader=" + p.classLoader);
                return;
            }
            log("FOUND clockClass=" + type.getName() + " superclass=" +
                (type.getSuperclass() == null ? "none" : type.getSuperclass().getName()));
            StringBuilder methods = new StringBuilder();
            for (Method m : type.getDeclaredMethods()) {
                String name = m.getName();
                if (name.contains("Clock") || name.contains("Time") ||
                    name.contains("Attach") || name.contains("Inflate") || name.contains("Visibility")) {
                    if (methods.length() > 0) methods.append(", ");
                    methods.append(name).append("(").append(m.getParameterTypes().length).append(")");
                }
            }
            log("declared clock/lifecycle methods=" + methods);
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllConstructors(type, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (constructed++ < 15 && param.thisObject instanceof View) {
                        log("clock CONSTRUCTED #" + constructed);
                        inspect((View)param.thisObject, "constructor");
                    }
                }
            });
            log("constructor hooks=" + hooks.size());
            hookLifecycle(type, "onAttachedToWindow", 0);
            hookClockMethod(type, "updateClock", false);
            hookClockMethod(type, "getSmallTime", true);
            hookClockMethod(type, "updateClockVisibility", false);
        } catch (Throwable t) { log("discovery error=" + t); }
    }

    private static void hookLifecycle(Class<?> type, String method, int mode) {
        try {
            XposedHelpers.findAndHookMethod(type, method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof View)) return;
                    if (mode == 0) {
                        if (attached++ < 15) inspect((View)param.thisObject, "attached");
                    } else {
                        if (inflated++ < 15) inspect((View)param.thisObject, "inflated");
                    }
                }
            });
            log("hook installed=" + method);
        } catch (Throwable t) { log("hook unavailable=" + method + " reason=" + t); }
    }

    private static void hookClockMethod(Class<?> type, String method, boolean smallTime) {
        try {
            XposedHelpers.findAndHookMethod(type, method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        int count = smallTime ? ++smallTimeCalls : ++clockUpdates;
                        if (count > 20) return;
                        log("method called=" + method + " count=" + count +
                            " receiver=" + param.thisObject.getClass().getName());
                        if (param.thisObject instanceof View)
                            inspect((View) param.thisObject, method);
                    } catch (Throwable t) { log("method callback error=" + method + " " + t); }
                }
            });
            log("hook installed=" + method);
        } catch (Throwable t) { log("hook unavailable=" + method + " reason=" + t); }
    }

    private static void inspect(View view, String event) {
        try {
            if ("constructor".equals(event)) {
                synchronized (SEEN) { if (!SEEN.add(view)) return; }
            }
            String id = "none";
            try { if (view.getId() != View.NO_ID) id = view.getResources().getResourceName(view.getId()); }
            catch (Resources.NotFoundException ignored) { }
            String parent = view.getParent() == null ? "none" : view.getParent().getClass().getName();
            int length = view instanceof TextView && ((TextView)view).getText() != null ?
                         ((TextView)view).getText().length() : 0;
            log(event + " type=" + view.getClass().getName() + " id=" + id +
                " parent=" + parent + " attached=" + view.isAttachedToWindow() +
                " textLength=" + length);
        } catch (Throwable t) { log("inspect error=" + t); }
    }
    private static void log(String s) { try { XposedBridge.log(TAG + s); } catch (Throwable ignored) { } }
}
