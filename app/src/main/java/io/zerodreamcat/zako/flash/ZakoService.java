package io.zerodreamcat.zako.flash;

import static io.zerodreamcat.zako.flash.App.TAG;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.util.List;

public final class ZakoService extends Service {
    private Process process;
    private final IRemoteService.Stub binder = new IRemoteService.Stub() {

        @Override
        public IRemoteProcess getRemoteProcess() {
            return new RemoteProcessHolder(process);
        }

        @Override
        public List<ActivityManager.RunningAppProcessInfo> getRunningAppProcesses() {
            return getSystemService(ActivityManager.class).getRunningAppProcesses();
        }
    };

    native static void root();

    public IBinder onBind(Intent intent) {
        try {
            root();
            process = Runtime.getRuntime().exec("sh");
            return binder;
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            return null;
        }
    }
}
