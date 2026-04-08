package io.zerodreamcat.zako.flash;

import static io.zerodreamcat.zako.flash.App.TAG;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;

import com.topjohnwu.superuser.CallbackList;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import io.zerodreamcat.zako.flash.databinding.ActivityMainBinding;

public final class MainActivity extends Activity {
    private ActivityMainBinding binding;
    private final List<String> console = new AppendCallbackList();

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            console.add(getString(R.string.service_connected));
            App.server = IRemoteService.Stub.asInterface(binder);
            // 不再自己创建 shell，直接使用 App.rootShell（由 ZakoService 保证）
            if (App.rootShell == null || !App.rootShell.isRoot()) {
                console.add("等待 root shell 就绪...");
                // 稍后重试（ZakoService 的 onBind 中会创建）
                binding.postDelayed(() -> check(), 500);
            } else {
                check();
            }
            getRunningAppProcesses();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            App.server = null;
            console.add(getString(R.string.service_disconnected));
        }
    };

    private boolean bind() {
        try {
            return bindIsolatedService(
                    new Intent(this, ZakoService.class),
                    Context.BIND_AUTO_CREATE,
                    "zakoflash",
                    getMainExecutor(),
                    connection
            );
        } catch (Exception e) {
            Log.e(TAG, "Can not bind service", e);
            return false;
        }
    }

    void getRunningAppProcesses() {
        try {
            var processes = App.server.getRunningAppProcesses();
            console.add("uid pid processName pkgList importance");
            for (var process : processes) {
                var str = String.format(Locale.ROOT, "%d %d %s %s %d",
                        process.uid, process.pid, process.processName,
                        Arrays.toString(process.pkgList), process.importance);
                console.add(str);
            }
        } catch (RemoteException | SecurityException e) {
            console.add(Log.getStackTraceString(e));
        }
    }

    void cmd(String... cmds) {
        if (App.rootShell == null) {
            console.add("Shell 未就绪");
            return;
        }
        App.rootShell.newJob().add(cmds).to(console).submit(out -> {
            if (!out.isSuccess()) {
                console.add(Arrays.toString(cmds) + getString(R.string.exec_failed));
            }
        });
    }

    void check() {
        if (App.rootShell == null || !App.rootShell.isRoot()) {
            console.add("Root shell 未就绪，无法继续");
            return;
        }
        cmd("id");
        if (App.rootShell.isRoot()) {
            console.add(getString(R.string.root_shell_opened));
        } else {
            console.add(getString(R.string.cannot_open_root_shell));
            return;
        }

        var cmd = "ps -A 2>/dev/null | grep magiskd | grep -qv grep";
        var magiskd = ShellUtils.fastCmdResult(App.rootShell, cmd);
        if (magiskd) {
            console.add(getString(R.string.magiskd_running));
            killMagiskd();
        } else {
            console.add(getString(R.string.magiskd_not_running));
            Backup();
        }
    }

    @SuppressLint("SetTextI18n")
    void killMagiskd() {
        binding.install.setOnClickListener(v -> {
            var cmd = "kill -9 $(pidof magiskd)";
            if (ShellUtils.fastCmdResult(App.rootShell, cmd)) {
                console.add(getString(R.string.magiskd_killed));
            } else {
                console.add(getString(R.string.magiskd_failed_to_kill));
            }
            binding.install.setEnabled(false);
        });
        binding.install.setText("Kill magiskd");
        binding.install.setVisibility(View.VISIBLE);
        binding.writeFrp.setVisibility(View.GONE);
    }

    @SuppressLint("SetTextI18n")
    void Backup() {
        if (App.rootShell == null || !App.rootShell.isRoot()) {
            console.add("Shell 未就绪或不是 root");
            return;
        }

        App.rootShell.newJob().add("mkdir -p /sdcard/ZakoFlash").exec();

        binding.install.setText("备份到 /sdcard/ZakoFlash");
        binding.install.setVisibility(View.VISIBLE);
        binding.install.setOnClickListener(v -> {
            binding.install.setEnabled(false);
            console.add(">>> 开始备份到 /sdcard/ZakoFlash <<<");

            String cmdBoot = "/system/bin/dd if=/dev/block/by-name/boot of=/sdcard/ZakoFlash/boot.img bs=4M 2>&1; echo $? > /sdcard/ZakoFlash/dd_boot.exit";
            String cmdFull = "/system/bin/dd if=/dev/block/mmcblk0 of=/sdcard/ZakoFlash/mmcblk0_head_32M.bin bs=1M count=32 2>&1; echo $? > /sdcard/ZakoFlash/dd_full.exit";
            String cmdFrp = "/system/bin/dd if=/dev/block/by-name/frp of=/sdcard/ZakoFlash/frp.img bs=4M 2>&1; echo $? > /sdcard/ZakoFlash/dd_frp.exit";

            Shell.Result resultBoot = App.rootShell.newJob().add(cmdBoot).exec();
            Shell.Result resultFull = App.rootShell.newJob().add(cmdFull).exec();
            Shell.Result resultFrp = App.rootShell.newJob().add(cmdFrp).exec();

            Shell.Result exitBoot = App.rootShell.newJob().add("cat /sdcard/ZakoFlash/dd_boot.exit 2>/dev/null").exec();
            Shell.Result exitFull = App.rootShell.newJob().add("cat /sdcard/ZakoFlash/dd_full.exit 2>/dev/null").exec();
            Shell.Result exitFrp = App.rootShell.newJob().add("cat /sdcard/ZakoFlash/dd_frp.exit 2>/dev/null").exec();

            console.add("Boot 分区备份:");
            if (exitBoot.getOut().isEmpty()) {
                console.add("退出码: (无法读取)");
            } else {
                console.add("退出码: " + exitBoot.getOut().get(0));
            }
            if (!resultBoot.getOut().isEmpty()) {
                for (String line : resultBoot.getOut()) console.add(line);
            }

            console.add("全盘前32M备份:");
            if (exitFull.getOut().isEmpty()) {
                console.add("退出码: (无法读取)");
            } else {
                console.add("退出码: " + exitFull.getOut().get(0));
            }
            if (!resultFull.getOut().isEmpty()) {
                for (String line : resultFull.getOut()) console.add(line);
            }

            console.add("FRP 分区备份:");
            if (exitFrp.getOut().isEmpty()) {
                console.add("退出码: (无法读取)");
            } else {
                console.add("退出码: " + exitFrp.getOut().get(0));
            }
            if (!resultFrp.getOut().isEmpty()) {
                for (String line : resultFrp.getOut()) console.add(line);
            }

            Shell.Result ls = App.rootShell.newJob().add("ls -l /sdcard/ZakoFlash/boot.img /sdcard/ZakoFlash/mmcblk0_head_32M.bin /sdcard/ZakoFlash/frp.img 2>&1").exec();
            console.add("生成的文件:");
            for (String line : ls.getOut()) console.add(line);

            console.add(">>> 备份完成 <<<");
            binding.install.setEnabled(true);
        });

        binding.writeFrp.setText("写入 FRP 分区");
        binding.writeFrp.setVisibility(View.VISIBLE);
        binding.writeFrp.setOnClickListener(v -> {
            binding.writeFrp.setEnabled(false);
            console.add(">>> 开始写入 FRP 分区 <<<");

            String frpPath = "/dev/block/by-name/frp";
            Shell.Result checkFrp1 = App.rootShell.newJob().add("ls " + frpPath + " 2>/dev/null").exec();
            if (!checkFrp1.isSuccess()) {
                frpPath = "/dev/block/bootdevice/by-name/frp";
            }
            Shell.Result checkFrp = App.rootShell.newJob().add("ls -l /sdcard/ZakoFlash/frp.img 2>/dev/null").exec();
            if (checkFrp.getOut().isEmpty()) {
                console.add("错误: /sdcard/ZakoFlash/frp.img 不存在，请先备份 FRP 分区");
                binding.writeFrp.setEnabled(true);
                return;
            }

            String cachePath = "/data/local/tmp/frp_mod.img";
            Shell.Result copyResult = App.rootShell.newJob().add("cp /sdcard/ZakoFlash/frp.img " + cachePath + " 2>&1").exec();
            if (!copyResult.isSuccess()) {
                console.add("错误: 复制 frp.img 到 cache 失败");
                for (String line : copyResult.getOut()) console.add(line);
                binding.writeFrp.setEnabled(true);
                return;
            }

            Shell.Result sizeResult = App.rootShell.newJob().add("stat -c%s " + cachePath + " 2>/dev/null").exec();
            if (sizeResult.getOut().isEmpty()) {
                console.add("错误: 无法获取文件大小");
                binding.writeFrp.setEnabled(true);
                return;
            }
            long size = Long.parseLong(sizeResult.getOut().get(0).trim());
            long seekPos = size - 1;
            Shell.Result modifyResult = App.rootShell.newJob().add("printf '\\x01' | dd of=" + cachePath + " bs=1 count=1 seek=" + seekPos + " conv=notrunc 2>&1").exec();
            if (!modifyResult.isSuccess()) {
                console.add("错误: 修改 frp 最后一位失败");
                for (String line : modifyResult.getOut()) console.add(line);
                binding.writeFrp.setEnabled(true);
                return;
            }
            String cmdWriteFrp = "/system/bin/dd if=" + cachePath + " of=" + frpPath + " bs=4M 2>&1; echo $? > /sdcard/ZakoFlash/dd_write_frp.exit";
            Shell.Result resultWriteFrp = App.rootShell.newJob().add(cmdWriteFrp).exec();
            Shell.Result exitWriteFrp = App.rootShell.newJob().add("cat /sdcard/ZakoFlash/dd_write_frp.exit 2>/dev/null").exec();

            console.add("FRP 分区写入:");
            if (exitWriteFrp.getOut().isEmpty()) {
                console.add("退出码: (无法读取)");
            } else {
                console.add("退出码: " + exitWriteFrp.getOut().get(0));
            }
            if (!resultWriteFrp.getOut().isEmpty()) {
                for (String line : resultWriteFrp.getOut()) console.add(line);
            }

            Shell.Result cleanResult = App.rootShell.newJob().add("rm " + cachePath + " 2>&1").exec();
            if (!cleanResult.isSuccess()) {
                console.add("警告: 清理 cache 失败");
                for (String line : cleanResult.getOut()) console.add(line);
            } else {
                console.add("已清理 cache 中的镜像");
            }

            console.add(">>> 写入完成 <<<");
            binding.writeFrp.setEnabled(true);
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 设置日志消费者，将 App 中历史日志显示到界面
        App.setLogConsumer(log -> {
            runOnUiThread(() -> {
                binding.console.append(log + "\n");
                binding.sv.fullScroll(ScrollView.FOCUS_DOWN);
            });
        });

        console.add(getString(R.string.start_service, Boolean.toString(bind())));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(connection);
    }

    class AppendCallbackList extends CallbackList<String> {
        @Override
        public void onAddElement(String s) {
            binding.console.append(s);
            binding.console.append("\n");
            binding.sv.postDelayed(() -> binding.sv.fullScroll(ScrollView.FOCUS_DOWN), 10);
        }
    }
}