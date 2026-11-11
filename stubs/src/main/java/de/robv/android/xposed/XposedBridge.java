package de.robv.android.xposed;
import java.util.Set;
public final class XposedBridge {
    public static void log(String message) {}
    public static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> type, XC_MethodHook hook) { return null; }
}
