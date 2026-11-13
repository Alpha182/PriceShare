package dev.priceshare.core;

import java.util.function.Consumer;

/** Logging without a logging library: the game-side code points these at the game's logger. */
public final class Log {
    private Log() {}

    public static volatile Consumer<String> info = s -> System.out.println("[Price Share] " + s);
    public static volatile Consumer<String> warn = s -> System.err.println("[Price Share] " + s);

    public static void info(String s) { info.accept(s); }
    public static void warn(String s) { warn.accept(s); }
}
