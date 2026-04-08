package io.zerodreamcat.zako.flash;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class CommandExecuteService extends Service {
    private static final String TAG = "CmdExecService";

    static {
        System.loadLibrary("zako"); // 确保 native 库被加载
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String cmd = intent.getStringExtra(CommandReceiver.EXTRA_CMD);
            if (cmd != null && !cmd.isEmpty()) {
                executeCommand(cmd);
            }
        }
        return START_NOT_STICKY;
    }

    private void executeCommand(String cmd) {
        StringBuilder output = new StringBuilder();

        // 1. 提权（如果当前进程不是 root）
        if (android.os.Process.myUid() != 0) {
            try {
                root();  // native 提权函数
                output.append("Rooted successfully.\n");
            } catch (Throwable e) {
                output.append("Root failed: ").append(e.getMessage()).append("\n");
                sendResultAndLog(output.toString());
                stopSelf();
                return;
            }
        } else {
            output.append("Already root.\n");
        }

        // 2. 执行命令
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = errReader.readLine()) != null) {
                output.append("[stderr] ").append(line).append("\n");
            }
            int exitCode = process.waitFor();
            output.append("Exit code: ").append(exitCode);
        } catch (Exception e) {
            output.append("Exception: ").append(e.getMessage());
        }

        sendResultAndLog(output.toString());
        stopSelf();
    }

    private void sendResultAndLog(String output) {
        // 打印到 logcat，方便查看
        Log.d(TAG, "Command result:\n" + output);
        // 通过广播回传结果
        Intent resultIntent = new Intent(CommandReceiver.ACTION_RESULT);
        resultIntent.putExtra(CommandReceiver.EXTRA_OUTPUT, output);
        sendBroadcast(resultIntent);
    }

    private native void root();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
