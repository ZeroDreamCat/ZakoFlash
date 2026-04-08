// CommandExecuteService.java
package io.zerodreamcat.zako.flash;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import com.topjohnwu.superuser.Shell;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CommandExecuteService extends Service {
    private String pendingCmd;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            pendingCmd = intent.getStringExtra(CommandReceiver.EXTRA_CMD);
        }
        // 在新的线程中执行，避免阻塞系统服务调用
        new Thread(this::executeCommand).start();
        return START_NOT_STICKY;
    }

    private void executeCommand() {
        App.addLog("CommandExecuteService: received command: " + pendingCmd);

        // 1. 确保 root shell 就绪
        if (!ensureRootShellReady()) {
            String error = "Failed to obtain root shell";
            App.addLog(error);
            sendResult(error);
            stopSelf();
            return;
        }

        // 2. 执行命令
        App.addLog("Executing: " + pendingCmd);
        Shell.Result result = App.rootShell.newJob().add(pendingCmd).exec();
        StringBuilder output = new StringBuilder();
        for (String line : result.getOut()) {
            output.append(line).append("\n");
        }
        if (!result.getErr().isEmpty()) {
            output.append("[stderr]\n");
            for (String line : result.getErr()) {
                output.append(line).append("\n");
            }
        }
        String outputStr = output.toString().trim();
        App.addLog("Command output:\n" + outputStr);
        sendResult(outputStr);
        stopSelf();
    }

    private boolean ensureRootShellReady() {
        // 如果全局 shell 已存在且有效，直接返回 true
        if (App.rootShell != null && App.rootShell.isRoot()) {
            App.addLog("Reusing existing root shell");
            return true;
        }

        // 否则绑定 ZakoService 来触发提权和 shell 创建
        App.addLog("No root shell available, binding to ZakoService...");
        final CountDownLatch latch = new CountDownLatch(1);
        ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                App.addLog("ZakoService connected, root shell should be ready");
                latch.countDown();
            }
            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };

        boolean bound = bindService(new Intent(this, ZakoService.class), conn, Context.BIND_AUTO_CREATE);
        if (!bound) {
            App.addLog("Failed to bind ZakoService");
            return false;
        }

        try {
            // 等待最多 10 秒，让服务完成提权和 shell 创建
            if (!latch.await(10, TimeUnit.SECONDS)) {
                App.addLog("Timeout waiting for ZakoService");
                return false;
            }
        } catch (InterruptedException e) {
            App.addLog("Interrupted while waiting for ZakoService");
            return false;
        } finally {
            unbindService(conn);
        }

        // 再次检查 shell 是否就绪
        if (App.rootShell != null && App.rootShell.isRoot()) {
            App.addLog("Root shell is ready after binding");
            return true;
        } else {
            App.addLog("Root shell still not ready after binding");
            return false;
        }
    }

    private void sendResult(String output) {
        Intent resultIntent = new Intent(CommandReceiver.ACTION_RESULT);
        resultIntent.putExtra(CommandReceiver.EXTRA_OUTPUT, output);
        sendBroadcast(resultIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}