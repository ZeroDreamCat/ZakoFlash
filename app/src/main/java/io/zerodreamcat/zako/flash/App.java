package io.zerodreamcat.zako.flash;

import android.app.Application;
import android.content.Context;
import com.topjohnwu.superuser.Shell;
import java.util.ArrayList;
import android.util.Log;
import java.util.List;
import java.util.function.Consumer;

public class App extends Application {
    public static final String TAG = "ZakoFlash";
    public static Context application; 
    public static IRemoteService server;   
    
    public static Shell rootShell;
    public static boolean isShellReady = false;

    private static final List<String> logCache = new ArrayList<>();
    private static Consumer<String> logConsumer = null;

    public App() {
        application = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // 如果有其他初始化可以放在这里
    }

    public static void addLog(String log) {
        Log.d(TAG, log);
        synchronized (logCache) {
            logCache.add(log);
            while (logCache.size() > 500) logCache.remove(0);
        }
        if (logConsumer != null) {
            logConsumer.accept(log);
        }
    }

    public static void setLogConsumer(Consumer<String> consumer) {
        logConsumer = consumer;
        synchronized (logCache) {
            for (String log : logCache) {
                if (consumer != null) consumer.accept(log);
            }
        }
    }

    public static void clearLogCache() {
        synchronized (logCache) {
            logCache.clear();
        }
    }
}
