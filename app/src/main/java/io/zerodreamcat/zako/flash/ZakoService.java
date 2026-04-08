package io.zerodreamcat.zako.flash;

import static io.zerodreamcat.zako.flash.App.TAG;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import com.topjohnwu.superuser.Shell;
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
        // 如果全局 root shell 已存在且有效，直接返回
        if (App.rootShell != null && App.rootShell.isRoot()) {
            App.addLog("ZakoService: root shell already ready");
            return;
        }
        App.addLog("ZakoService: creating root shell via exploit...");
        try {
            // 1. 执行漏洞提权，使本进程获得 root 权限
            root();
            // 2. 创建 libsu shell（此时进程已经是 root，即使 FLAG_NON_ROOT_SHELL 也会得到 root shell）
            App.rootShell = Shell.Builder.create()
                    .setFlags(Shell.FLAG_NON_ROOT_SHELL)
                    .build();
            App.isShellReady = App.rootShell.isRoot();
            if (App.isShellReady) {
                App.addLog("ZakoService: root shell created successfully");
            } else {
                App.addLog("ZakoService: root shell creation FAILED (not root)");
            }
            // 3. 保留原有的 process 对象（供 RemoteProcessHolder 使用）
            process = Runtime.getRuntime().exec("sh");
        } catch (Exception e) {
            App.addLog("ZakoService: exception while creating root shell: " + e.getMessage());
            Log.e(TAG, "Failed to create root shell", e);
        }
    }
}