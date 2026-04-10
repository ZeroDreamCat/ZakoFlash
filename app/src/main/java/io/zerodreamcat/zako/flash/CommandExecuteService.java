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
        System.loadLibrary("zako");
        registerNative();  
    }

    private static native void registerNative();
    private static native void root();

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
        
        if (android.os.Process.myUid() != 0) {
            try {
                root();
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
        Log.d(TAG, "Command result:\n" + output);
        Intent resultIntent = new Intent(CommandReceiver.ACTION_RESULT);
        resultIntent.putExtra(CommandReceiver.EXTRA_OUTPUT, output);
        sendBroadcast(resultIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
