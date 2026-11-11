package dev.vector.oneuilyrics;

import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
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

/** Temporary 8-second status bar clock label test; no persistence. */
public final class ClockProbe implements IXposedHookLoadPackage {
    private static final String TAG = "OneUILyricsProbe: ";
    private static final String TARGET = "com.android.systemui.statusbar.policy.Clock";
    private static final Set<View> SEEN = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private static int constructed = 0;
    private static int attached = 0;
    private static int clockUpdates = 0;
    private static int smallTimeCalls = 0;
    private static int hierarchyDumps = 0;
    private static boolean testApplied = false;
    private static final String TEST_LABEL = "♪ LYRIC TEST ♪";
    private static final String[] ROOTS = {
        "com.android.systemui.statusbar.phone.PhoneStatusBarView",
        "com.android.systemui.statusbar.phone.KeyguardStatusBarView",
        "com.android.systemui.statusbar.phone.StatusBarWindowView",
        "com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment",
        "com.android.systemui.statusbar.phone.StatusBarIconControllerImpl"
    };

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
                        scheduleTest((View)param.thisObject, "Clock constructor");
                    }
                }
            });
            log("constructor hooks=" + hooks.size());
            hookLifecycle(type, "onAttachedToWindow", 0);
            hookClockMethod(type, "updateClock", false);
            hookClockMethod(type, "getSmallTime", true);
            hookClockMethod(type, "updateClockVisibility", false);
            probeStatusBarRoots(p.classLoader);
        } catch (Throwable t) { log("discovery error=" + t); }
    }

    private static void hookLifecycle(Class<?> type, String method, int mode) {
        try {
            XposedHelpers.findAndHookMethod(type, method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof View && attached++ < 15)
                        inspect((View)param.thisObject, "attached");
                    if (param.thisObject instanceof View)
                        scheduleTest((View)param.thisObject, "Clock attachment");
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
                        if (param.thisObject instanceof View)
                            scheduleTest((View) param.thisObject, method);
                    } catch (Throwable t) { log("method callback error=" + method + " " + t); }
                }
            });
            log("hook installed=" + method);
        } catch (Throwable t) { log("hook unavailable=" + method + " reason=" + t); }
    }

    private static void probeStatusBarRoots(ClassLoader loader) {
        for (String candidate : ROOTS) {
            try {
                Class<?> cls = XposedHelpers.findClassIfExists(candidate, loader);
                if (cls == null) { log("statusbar candidate absent=" + candidate); continue; }
                log("statusbar candidate FOUND=" + candidate);
                if (candidate.endsWith("CollapsedStatusBarFragment")) {
                    hookCollapsedFragment(cls);
                    continue;
                }
                if (!candidate.endsWith("PhoneStatusBarView") && !candidate.endsWith("StatusBarWindowView")) continue;
                if (!ViewGroup.class.isAssignableFrom(cls)) continue;
                try {
                    XposedHelpers.findAndHookMethod(cls, "onAttachedToWindow", new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (!(param.thisObject instanceof ViewGroup) || hierarchyDumps++ >= 3) return;
                                ViewGroup root = (ViewGroup) param.thisObject;
                                log("statusbar tree ROOT=" + root.getClass().getName());
                                int[] remaining = {100};
                                dumpTree(root, 0, remaining);
                                findClockAndTest(root, 0, new int[] {150});
                            } catch (Throwable t) { log("statusbar tree error=" + t); }
                        }
                    });
                    log("statusbar attached hook installed=" + candidate);
                } catch (Throwable t) { log("statusbar hook unavailable=" + candidate + " " + t); }
            } catch (Throwable t) { log("statusbar lookup failed=" + candidate + " " + t); }
        }
    }

    // The fragment hook is only used for the collapsed (top) status bar.
    private static void hookCollapsedFragment(Class<?> type) {
        try {
            XposedHelpers.findAndHookMethod(type, "onViewCreated",
                View.class, Bundle.class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (param.args == null || param.args.length == 0 ||
                                !(param.args[0] instanceof View)) return;
                        View root = (View)param.args[0];
                        log("collapsed fragment onViewCreated root=" + root.getClass().getName());
                        if (hierarchyDumps++ < 3) dumpTree(root, 0, new int[]{100});
                        findClockAndTest(root, 0, new int[]{150});
                    }
                });
            log("hook installed=CollapsedStatusBarFragment.onViewCreated");
        } catch (Throwable t) {
            log("fragment hook unavailable=" + t);
        }
    }

    private static void findClockAndTest(View view, int depth, int[] budget) {
        if (view == null || depth > 10 || budget[0]-- <= 0 || testApplied) return;
        if (view instanceof TextView && isClockCandidate(view)) {
            scheduleTest(view, "collapsed status bar tree");
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount() && budget[0] > 0; i++)
                findClockAndTest(group.getChildAt(i), depth + 1, budget);
        }
    }

    private static boolean isClockCandidate(View view) {
        if (!(view instanceof TextView)) return false;
        if (TARGET.equals(view.getClass().getName())) return true;
        if (view.getId() == View.NO_ID) return false;
        try {
            String name = view.getResources().getResourceEntryName(view.getId()).toLowerCase(java.util.Locale.ROOT);
            return name.equals("clock") || name.equals("status_bar_clock") ||
                   name.equals("statusbar_clock") || name.equals("clock_view");
        } catch (Throwable ignored) { return false; }
    }

    /** Short, bounded UI-thread test: keep the label visible for ~6 seconds, then restore. */
    private static void scheduleTest(View view, String source) {
        if (!(view instanceof TextView) || testApplied) return;
        view.postDelayed(() -> {
            if (testApplied || !view.isAttachedToWindow() || !isClockCandidate(view)) return;
            final TextView clock = (TextView) view;
            final CharSequence original = clock.getText();
            testApplied = true;
            log("TEST BEGIN source=" + source + " id=" + safeId(view) +
                " type=" + view.getClass().getName());
            final Runnable[] task = new Runnable[1];
            task[0] = new Runnable() {
                int ticks = 0;
                int overwrites = 0;
                @Override public void run() {
                    try {
                        if (!clock.isAttachedToWindow()) {
                            log("TEST STOP: clock detached");
                            return;
                        }
                        if (ticks >= 24) {
                            // Never leave test text behind. Real clock refresh may also restore it.
                            if (TEST_LABEL.contentEquals(clock.getText())) clock.setText(original);
                            log("TEST END restored=" + !TEST_LABEL.contentEquals(clock.getText()) +
                                " SamsungOverwrites=" + overwrites);
                            return;
                        }
                        if (!TEST_LABEL.contentEquals(clock.getText())) overwrites++;
                        clock.setText(TEST_LABEL);
                        if (ticks == 0) log("TEST APPLIED id=" + safeId(view));
                        ticks++;
                        clock.postDelayed(this, 250L);
                    } catch (Throwable t) { log("TEST error=" + t); }
                }
            };
            clock.post(task[0]);
        }, 350L);
    }

    private static String safeId(View view) {
        try { return view.getResources().getResourceName(view.getId()); }
        catch (Throwable t) { return Integer.toHexString(view.getId()); }
    }

    private static void dumpTree(View view, int depth, int[] remaining) {
        if (depth > 6 || remaining[0]-- <= 0) return;
        try {
            String id = "none";
            if (view.getId() != View.NO_ID) {
                try { id = view.getResources().getResourceName(view.getId()); }
                catch (Throwable ignored) { id = Integer.toHexString(view.getId()); }
            }
            log("tree depth=" + depth + " type=" + view.getClass().getName() +
                " id=" + id + " visibility=" + view.getVisibility());
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                int children = Math.min(group.getChildCount(), 60);
                for (int i = 0; i < children && remaining[0] > 0; i++)
                    dumpTree(group.getChildAt(i), depth + 1, remaining);
            }
        } catch (Throwable t) { log("tree node error=" + t); }
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
