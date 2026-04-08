package io.zerodreamcat.zako.flash;

import static io.zerodreamcat.zako.flash.App.TAG;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.NoShellException;
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

    @Override
    public IBinder onBind(Intent intent) {
        ensureRootShell();
        return binder;
    }

    private synchronized void ensureRootShell() {
        if (App.rootShell != null && App.rootShell.isRoot()) {
            App.addLog("ZakoService: root shell already ready");
            return;
        }
        App.addLog("ZakoService: creating root shell via exploit...");
        try {
            App.addLog("ZakoService running in process: " + android.os.Process.myPid());
            // 记录提权前的 uid
            App.addLog("Before root, uid=" + android.os.Process.myUid());
            root();   // native 提权
            App.addLog("After root, uid=" + android.os.Process.myUid());
    
            // 不要使用 FLAG_NON_ROOT_SHELL，让 libsu 自动选择 su 或 sh
            App.rootShell = Shell.Builder.create().build();
            App.isShellReady = (App.rootShell != null && App.rootShell.isRoot());
            if (App.isShellReady) {
                App.addLog("ZakoService: root shell created successfully");
            } else {
                App.addLog("ZakoService: root shell creation FAILED (not root or null)");
            }
            // 保留原有 process 供 RemoteProcessHolder 使用（如果需要）
            process = Runtime.getRuntime().exec("sh");
        } catch (NoShellException e) {
            App.addLog("ZakoService: NoShellException - " + e.getMessage());
            Log.e(TAG, "NoShellException", e);
        } catch (Exception e) {
            App.addLog("ZakoService: exception - " + e.getMessage());
            Log.e(TAG, "Failed to create root shell", e);
        }
    }
}