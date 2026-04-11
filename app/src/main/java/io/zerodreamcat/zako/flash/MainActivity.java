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
import java.util.Collections;
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
				App.rootShell = shell;
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
			console.add("ZakoFlash by Github@ZeroDreamCat");
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
                // console.add(Arrays.toString(cmds) + getString(R.string.exec_failed));
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
            Backup();
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

	@SuppressLint({"SetTextI18n", "SdCardPath"})
	void Backup() {
		if (shell == null || !shell.isRoot()) {
			console.add("Shell 未就绪或不是 root");
			return;
		}
		
        shell.newJob().add("mkdir -p /sdcard/ZakoFlash").exec();

		binding.install.setText("备份到 /sdcard/ZakoFlash");
		binding.install.setVisibility(View.VISIBLE);
		binding.install.setOnClickListener(v -> {
			binding.install.setEnabled(false);
			console.add(">>> 开始备份到 /sdcard/ZakoFlash <<<");

			String cmdBoot = "/system/bin/dd if=/dev/block/by-name/boot of=/sdcard/ZakoFlash/boot.img bs=4M 2>&1";
			String cmdFull = "/system/bin/dd if=/dev/block/mmcblk0 of=/sdcard/ZakoFlash/mmcblk0_head_32M.bin bs=1M count=32 2>&1";
			String cmdFrp = "/system/bin/dd if=/dev/block/by-name/frp of=/sdcard/ZakoFlash/frp.img bs=4M 2>&1";
			
			Shell.Result resultBoot = shell.newJob().add(cmdBoot).exec();
			Shell.Result resultFull = shell.newJob().add(cmdFull).exec();
			Shell.Result resultFrp = shell.newJob().add(cmdFrp).exec();

            String exitBoot = String.valueOf(resultBoot.getCode());
            String exitFull = String.valueOf(resultFull.getCode());
            String exitFrp = String.valueOf(resultFrp.getCode());
			//Shell.Result exitBoot = shell.newJob().add("cat /sdcard/ZakoFlash/dd_boot.exit").exec();
			//Shell.Result exitFull = shell.newJob().add("cat /sdcard/ZakoFlash/dd_full.exit 2>/dev/null").exec();
			//Shell.Result exitFrp = shell.newJob().add("cat /sdcard/ZakoFlash/dd_frp.exit 2>/dev/null").exec();

			console.add("Boot 分区备份:");
            console.add("退出码: " + exitBoot);
            // Print output of dd
			if (!resultBoot.getOut().isEmpty()) {
                console.addAll(resultBoot.getOut());
			}

			console.add("mmcblk0前32M备份:");
            console.add("退出码: " + exitFull);
            if (!resultFull.getOut().isEmpty()) {
                console.addAll(resultFull.getOut());
			}

			console.add("FRP 分区备份:");
            console.add("退出码: " + exitFrp);
            if (!resultFrp.getOut().isEmpty()) {
                console.addAll(resultFrp.getOut());
			}

			Shell.Result ls = shell.newJob().add("ls -l /sdcard/ZakoFlash/boot.img /sdcard/ZakoFlash/mmcblk0_head_32M.bin /sdcard/ZakoFlash/frp.img 2>&1").exec();
			console.add("生成的文件:");
            console.addAll(ls.getOut());

			console.add(">>> 备份完成 <<<");
			binding.install.setEnabled(true);
		});

		binding.writeFrp.setText("写入 FRP 分区");
		binding.writeFrp.setVisibility(View.VISIBLE);
		binding.writeFrp.setOnClickListener(v -> {
            binding.writeFrp.setText("写入 FRP 分区");
            binding.writeFrp.setVisibility(View.VISIBLE);
            binding.writeFrp.setOnClickListener(w -> {
                binding.writeFrp.setEnabled(false);
                console.add(">>> 开始写入 FRP 分区 <<<");

                String frpPath = "/dev/block/by-name/frp";
                Shell.Result checkFrp1 = shell.newJob().add("ls " + frpPath + " 2>/dev/null").exec();
                if (!checkFrp1.isSuccess()) {
                    frpPath = "/dev/block/bootdevice/by-name/frp";
                }

                String backupImg = "/sdcard/ZakoFlash/frp.img";
                Shell.Result checkImg = shell.newJob().add("test -f " + backupImg + " && echo 'exists'").exec();
                if (checkImg.getCode() != 0) {
                    console.add("错误: " + backupImg + " 不存在，请先执行备份");
                    binding.writeFrp.setEnabled(true);
                    return;
                }

                String cachePath = "/sdcard/ZakoFlash/frp_mod.img";
                String statFile = "/sdcard/ZakoFlash/stat.us";

                String shellScript =
                        "cp " + backupImg + " " + cachePath + " 2>&1 || { echo 'copy failed'; exit 1; };" +
                                "stat -c%s " + cachePath + " > " + statFile + " 2>&1 || { echo 'get stat failed'; exit 1; };" +
                                "size=$(cat " + statFile + ");" +
                                "if [ -z \"$size\" ]; then echo 'file empty'; exit 1; fi;" +
                                "lastByteOffset=$((size - 1));" +
                                "lastByte=$(dd if=" + cachePath + " bs=1 count=1 skip=$lastByteOffset 2>/dev/null | od -An -tx1 | tr -d ' \\n');" +
                                "if [ \"$lastByte\" = \"01\" ]; then" +
                                "    echo 'no need';" +
                                "    rm -f " + cachePath + ";" +
                                "    exit 2;" +
                                "fi;" +
                                "printf '\\x01' | dd of=" + cachePath + " bs=1 count=1 seek=$lastByteOffset conv=notrunc 2>&1 || { echo 'failed'; exit 1; };" +
                                "dd if=" + cachePath + " of=" + frpPath + " bs=4M 2>&1 || { echo 'write failed'; exit 1; };" +
                                "rm -f " + cachePath + " && echo 'done'";

                shell.newJob()
                        .add("sh -c", shellScript)
                        .to(console)
                        .submit(result -> {
                            int code = result.getCode();
                            if (code == 0) {
                                console.add(">>> 写入完成 <<<");
                            } else if (code == 2) {
                                console.add(">>> FRP 已修改过，跳过修改<<<");
                            } else {
                                console.add(">>> 写入失败 <<<");
                            }
                            binding.writeFrp.setEnabled(true);
                        });
            });
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
