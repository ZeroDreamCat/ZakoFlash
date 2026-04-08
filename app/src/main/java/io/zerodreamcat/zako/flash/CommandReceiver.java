// CommandReceiver.java
package io.zerodreamcat.zako.flash;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CommandReceiver extends BroadcastReceiver {
    public static final String ACTION_EXEC = "io.zerodreamcat.zako.flash.action.EXEC";
    public static final String ACTION_RESULT = "io.zerodreamcat.zako.flash.action.RESULT";
    public static final String EXTRA_CMD = "cmd";
    public static final String EXTRA_OUTPUT = "output";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!ACTION_EXEC.equals(intent.getAction())) return;

        String cmd = intent.getStringExtra(EXTRA_CMD);
        if (cmd == null || cmd.trim().isEmpty()) {
            sendResult(context, "Error: empty command");
            return;
        }

        // 启动服务执行命令，避免阻塞广播线程
        Intent serviceIntent = new Intent(context, CommandExecuteService.class);
        serviceIntent.putExtra(EXTRA_CMD, cmd);
        context.startService(serviceIntent);
    }

    private void sendResult(Context context, String output) {
        Intent resultIntent = new Intent(ACTION_RESULT);
        resultIntent.putExtra(EXTRA_OUTPUT, output);
        context.sendBroadcast(resultIntent);
    }
}