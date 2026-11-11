package dev.vector.oneuilyrics;

import android.content.res.Resources;
import android.view.View;
import android.widget.TextView;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Read-only discovery; does not override clock text. */
public final class ClockProbe implements IXposedHookLoadPackage {
    private static final String TAG = "OneUILyricsProbe: ";
    private static final String[] CANDIDATES = {
        "com.android.systemui.statusbar.policy.Clock",
        "com.android.systemui.statusbar.phone.Clock",
        "com.android.systemui.statusbar.views.Clock",
        "com.android.systemui.statusbar.phone.ui.Clock"
    };
    private static final Set<View> SEEN = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        if (!"com.android.systemui".equals(p.packageName)) return;
        log("loaded process=" + p.processName + " classLoader=" + p.classLoader);
        for (String name : CANDIDATES) {
            try {
                Class<?> type = XposedHelpers.findClassIfExists(name, p.classLoader);
                if (type == null || !View.class.isAssignableFrom(type)) {
                    log("candidate absent=" + name);
                    continue;
                }
                log("candidate FOUND=" + type.getName());
                XposedHelpers.findAndHookMethod(type, "onAttachedToWindow", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (param.thisObject instanceof View) inspect((View) param.thisObject);
                        } catch (Throwable t) { log("inspect failed=" + t); }
                    }
                });
            } catch (Throwable t) { log("candidate hook failed=" + name + " : " + t); }
        }
    }

    private static void inspect(View view) {
        synchronized (SEEN) { if (!SEEN.add(view)) return; }
        String idName = "(none)";
        try { if (view.getId() != View.NO_ID) idName = view.getResources().getResourceName(view.getId()); }
        catch (Resources.NotFoundException ignored) { }
        catch (Throwable ignored) { }
        String parent = "(none)";
        try { if (view.getParent() != null) parent = view.getParent().getClass().getName(); }
        catch (Throwable ignored) { }
        int textLength = 0;
        try { if (view instanceof TextView) textLength = ((TextView) view).getText().length(); }
        catch (Throwable ignored) { }
        log("view attached type=" + view.getClass().getName() +
            " id=" + idName + " parent=" + parent + " textLength=" + textLength);
    }
    private static void log(String s) { try { XposedBridge.log(TAG + s); } catch (Throwable ignored) { } }
}
