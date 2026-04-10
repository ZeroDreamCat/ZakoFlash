package io.zerodreamcat.zako.flash;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.topjohnwu.superuser.Shell;

public class CommandExecuteService extends Service {
    private static final String TAG = "CmdExecService";

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

        if (App.rootShell == null) {
            output.append("Error: root shell not initialized. Please launch the app first.\n");
            sendResultAndLog(output.toString());
            stopSelf();
            return;
        }

        if (!App.rootShell.isRoot()) {
            output.append("Error: shell is not root.\n");
            sendResultAndLog(output.toString());
            stopSelf();
            return;
        }

        Shell.Result result = App.rootShell.newJob().add(cmd).exec();
        for (String line : result.getOut()) {
            output.append(line).append("\n");
        }
        if (!result.getErr().isEmpty()) {
            output.append("[stderr]\n");
            for (String line : result.getErr()) {
                output.append(line).append("\n");
            }
        }
        output.append("Exit code: ").append(result.getCode());
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
