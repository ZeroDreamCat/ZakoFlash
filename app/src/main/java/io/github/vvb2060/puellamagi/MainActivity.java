package io.github.vvb2060.puellamagi;

import static io.github.vvb2060.puellamagi.App.TAG;

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
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

import io.github.vvb2060.puellamagi.databinding.ActivityMainBinding;

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
            shell = Shell.Builder.create().build();
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
                    new Intent(this, MagicaService.class),
                    Context.BIND_AUTO_CREATE,
                    "magica",
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
    }

	@SuppressLint("SetTextI18n")
	void installMagisk() {
		String cmdFilePath = "/sdcard/my_commands.txt";

		// 检查 shell 状态
		if (shell == null) {
			console.add("Shell 对象为 null");
			return;
		}
		console.add("Shell 状态: " + (shell.isRoot() ? "Root" : "Non-root"));

		// 尝试其他可能的路径（某些设备 /sdcard 可能指向不同位置）
		String[] possiblePaths = {
			"/sdcard/my_commands.txt",
			"/storage/emulated/0/my_commands.txt",
			"/mnt/sdcard/my_commands.txt"
		};

		String actualPath = null;
		for (String path : possiblePaths) {
			Shell.Result testResult = shell.newJob().add("test -f " + path + " && echo EXISTS").exec();
			if (testResult.getOut().contains("EXISTS")) {
				actualPath = path;
				break;
			}
		}

		if (actualPath == null) {
			console.add("在所有常见路径下都未找到命令文件");
			// 列出 /sdcard 目录内容帮助排查
			Shell.Result lsResult = shell.newJob().add("ls -l /sdcard/").exec();
			console.add("/sdcard/ 目录内容:");
			for (String line : lsResult.getOut()) {
				console.add(line);
			}
			for (String err : lsResult.getErr()) {
				console.add("ls stderr: " + err);
			}
			return;
		}

		console.add("找到命令文件: " + actualPath);

		// 直接读取文件内容
		Shell.Result catResult = shell.newJob().add("cat " + actualPath).exec();
		console.add("cat 退出码: " + catResult.getCode());
		if (catResult.getCode() != 0) {
			console.add("读取文件失败，stderr: " + catResult.getErr());
			return;
		}

		// 解析命令
		List<String> commands = new ArrayList<>();
		for (String line : catResult.getOut()) {
			line = line.trim();
			if (!line.isEmpty() && !line.startsWith("#")) {
				commands.add(line);
			}
		}

		if (commands.isEmpty()) {
			console.add("命令文件为空，没有可执行的命令");
			return;
		}

		console.add("找到 " + commands.size() + " 条命令，点击下方按钮执行");
		binding.install.setText("执行自定义命令");
		binding.install.setVisibility(View.VISIBLE);
		binding.install.setOnClickListener(v -> {
			binding.install.setEnabled(false);
			console.add(">>> 开始执行自定义命令 <<<");
			shell.newJob().add(commands.toArray(new String[0])).to(console).submit(result -> {
				if (result.isSuccess()) {
					console.add(">>> 所有命令执行成功 <<<");
				} else {
					console.add(">>> 部分命令执行失败，退出码：" + result.getCode() + " <<<");
				}
				binding.install.setEnabled(true);
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
