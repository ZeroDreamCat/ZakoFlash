package io.zerodreamcat.zako.flash;

import static io.zerodreamcat.zako.flash.App.TAG;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;

import com.topjohnwu.superuser.CallbackList;
import com.topjohnwu.superuser.NoShellException;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

import io.zerodreamcat.zako.flash.databinding.ActivityMainBinding;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private Shell shell;
    private ActivityMainBinding binding;
    private final List<String> console = new AppendCallbackList();
    private final ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            console.add(getString(R.string.service_connected));
            App.server = IRemoteService.Stub.asInterface(binder);
            Shell.enableVerboseLogging = BuildConfig.DEBUG;
            try {
                shell = Shell.Builder.create()
                        .setFlags(Shell.FLAG_NON_ROOT_SHELL)
                        .build();
            } catch (NoShellException e) {
                Log.e(TAG, "Unable to create shell", e);
                console.add("无法创建 shell: " + e.getMessage());
                return;
            }
            check();
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
        shell.newJob().add(cmds).to(console).submit(out -> {
            if (!out.isSuccess()) {
                console.add(Arrays.toString(cmds) + getString(R.string.exec_failed));
            }
        });
    }

    void check() {
        cmd("id");
        if (shell.isRoot()) {
            console.add(getString(R.string.root_shell_opened));
        } else {
            console.add(getString(R.string.cannot_open_root_shell));
            return;
        }

        var cmd = "ps -A 2>/dev/null | grep magiskd | grep -qv grep";
        var magiskd = ShellUtils.fastCmdResult(shell, cmd);
        if (magiskd) {
            console.add(getString(R.string.magiskd_running));
            killMagiskd();
        } else {
            console.add(getString(R.string.magiskd_not_running));
            installMagisk();
        }
    }


    @SuppressLint("SetTextI18n")
    void killMagiskd() {
        binding.install.setOnClickListener(v -> {
            var cmd = "kill -9 $(pidof magiskd)";
            if (ShellUtils.fastCmdResult(shell, cmd)) {
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
	void installMagisk() {
		if (shell == null || !shell.isRoot()) {
			console.add("Shell 未就绪或不是 root");
			return;
		}

		binding.install.setText("备份到 /sdcard");
		binding.install.setVisibility(View.VISIBLE);
		binding.install.setOnClickListener(v -> {
			binding.install.setEnabled(false);
			console.add(">>> 开始备份到 /sdcard <<<");

			// 确定 FRP 分区路径
			String frpPath = "/dev/block/by-name/frp";
			Shell.Result checkFrp1 = shell.newJob().add("ls " + frpPath + " 2>/dev/null").exec();
			if (!checkFrp1.isSuccess()) {
				frpPath = "/dev/block/bootdevice/by-name/frp";
			}

			// 备份 boot 分区 (mmcblk0p3)
			String cmdBoot = "/system/bin/dd if=/dev/block/mmcblk0p3 of=/sdcard/boot.img bs=4M 2>&1; echo $? > /sdcard/dd_boot.exit";
			// 备份 mmcblk0 前 8M
			String cmdFull = "/system/bin/dd if=/dev/block/mmcblk0 of=/sdcard/mmcblk0_head_8M.bin bs=1M count=8 2>&1; echo $? > /sdcard/dd_full.exit";
			// 备份 FRP 分区
			String cmdFrp = "/system/bin/dd if=" + frpPath + " of=/sdcard/frp.img bs=4M 2>&1; echo $? > /sdcard/dd_frp.exit";

			// 执行命令（同步执行，确保完成）
			Shell.Result resultBoot = shell.newJob().add(cmdBoot).exec();
			Shell.Result resultFull = shell.newJob().add(cmdFull).exec();
			Shell.Result resultFrp = shell.newJob().add(cmdFrp).exec();

			// 读取退出码
			Shell.Result exitBoot = shell.newJob().add("cat /sdcard/dd_boot.exit 2>/dev/null").exec();
			Shell.Result exitFull = shell.newJob().add("cat /sdcard/dd_full.exit 2>/dev/null").exec();
			Shell.Result exitFrp = shell.newJob().add("cat /sdcard/dd_frp.exit 2>/dev/null").exec();

			console.add("Boot 分区备份:");
			if (exitBoot.getOut().isEmpty()) {
				console.add("退出码: (无法读取)");
			} else {
				console.add("退出码: " + exitBoot.getOut().get(0));
			}
			// 打印 dd 的输出（如果有）
			if (!resultBoot.getOut().isEmpty()) {
				for (String line : resultBoot.getOut()) console.add(line);
			}

			console.add("全盘前8M备份:");
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

			// 列出生成的文件
			Shell.Result ls = shell.newJob().add("ls -l /sdcard/boot.img /sdcard/mmcblk0_head_8M.bin /sdcard/frp.img 2>&1").exec();
			console.add("生成的文件:");
			for (String line : ls.getOut()) console.add(line);

			console.add(">>> 备份完成 <<<");
			binding.install.setEnabled(true);
		});

		// 设置写入 FRP 按钮
		binding.writeFrp.setText("写入 FRP 分区");
		binding.writeFrp.setVisibility(View.VISIBLE);
		binding.writeFrp.setOnClickListener(v -> {
			binding.writeFrp.setEnabled(false);
			console.add(">>> 开始写入 FRP 分区 <<<");

			// 确定 FRP 分区路径
			String frpPath = "/dev/block/by-name/frp";
			Shell.Result checkFrp1 = shell.newJob().add("ls " + frpPath + " 2>/dev/null").exec();
			if (!checkFrp1.isSuccess()) {
				frpPath = "/dev/block/bootdevice/by-name/frp";
			}
			Shell.Result checkFrp = shell.newJob().add("ls -l /sdcard/frp.img 2>/dev/null").exec();
			if (checkFrp.getOut().isEmpty()) {
				console.add("错误: /sdcard/frp.img 不存在，请先备份 FRP 分区");
				binding.writeFrp.setEnabled(true);
				return;
			}

			String cachePath = "/data/cache/frp_mod.img";
			Shell.Result copyResult = shell.newJob().add("cp /sdcard/frp.img " + cachePath + " 2>&1").exec();
			if (!copyResult.isSuccess()) {
				console.add("错误: 复制 frp.img 到 cache 失败");
				for (String line : copyResult.getOut()) console.add(line);
				binding.writeFrp.setEnabled(true);
				return;
			}

			Shell.Result sizeResult = shell.newJob().add("stat -c%s " + cachePath + " 2>/dev/null").exec();
			if (sizeResult.getOut().isEmpty()) {
				console.add("错误: 无法获取文件大小");
				binding.writeFrp.setEnabled(true);
				return;
			}
			long size = Long.parseLong(sizeResult.getOut().get(0).trim());
			long seekPos = size - 1;
			Shell.Result modifyResult = shell.newJob().add("printf '\\x01' | dd of=" + cachePath + " bs=1 count=1 seek=" + seekPos + " conv=notrunc 2>&1").exec();
			if (!modifyResult.isSuccess()) {
				console.add("错误: 修改 frp 最后一位失败");
				for (String line : modifyResult.getOut()) console.add(line);
				binding.writeFrp.setEnabled(true);
				return;
			}
			String cmdWriteFrp = "/system/bin/dd if=" + cachePath + " of=" + frpPath + " bs=4M 2>&1; echo $? > /sdcard/dd_write_frp.exit";
			Shell.Result resultWriteFrp = shell.newJob().add(cmdWriteFrp).exec();
			Shell.Result exitWriteFrp = shell.newJob().add("cat /sdcard/dd_write_frp.exit 2>/dev/null").exec();

			console.add("FRP 分区写入:");
			if (exitWriteFrp.getOut().isEmpty()) {
				console.add("退出码: (无法读取)");
			} else {
				console.add("退出码: " + exitWriteFrp.getOut().get(0));
			}
			if (!resultWriteFrp.getOut().isEmpty()) {
				for (String line : resultWriteFrp.getOut()) console.add(line);
			}

			Shell.Result cleanResult = shell.newJob().add("rm " + cachePath + " 2>&1").exec();
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
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
