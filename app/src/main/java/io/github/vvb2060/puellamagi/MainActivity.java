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
		if (shell == null) {
			console.add("Shell 对象为 null");
			return;
		}
		console.add("Shell 状态: " + (shell.isRoot() ? "Root" : "Non-root"));

		// 优先推荐用户将文件放在 /data/local/tmp/ 下
		String[] possiblePaths = {
			"/data/local/tmp/my_commands.txt",
			"/sdcard/my_commands.txt",
			"/storage/emulated/0/my_commands.txt"
		};

		String actualPath = null;
		String fileContent = null;

		// 尝试用 cat 读取文件，不依赖 test
		for (String path : possiblePaths) {
			// 使用完整路径的 cat（避免别名干扰）
			Shell.Result catResult = shell.newJob().add("/system/bin/cat " + path + " 2>/dev/null").exec();
			if (catResult.getCode() == 0 && !catResult.getOut().isEmpty()) {
				actualPath = path;
				fileContent = String.join("\n", catResult.getOut());
				break;
			}
			// 如果 /system/bin/cat 不存在，尝试普通 cat
			catResult = shell.newJob().add("cat " + path + " 2>/dev/null").exec();
			if (catResult.getCode() == 0 && !catResult.getOut().isEmpty()) {
				actualPath = path;
				fileContent = String.join("\n", catResult.getOut());
				break;
			}
		}

		if (actualPath == null) {
			console.add("未找到命令文件。请将命令文件放置于以下任一位置：");
			for (String p : possiblePaths) console.add("  " + p);
			console.add("文件格式：每行一条命令，支持 # 注释");
			// 额外提示：也可以使用输入框手动执行
			console.add("提示：你也可以使用界面上的输入框直接执行命令");
			return;
		}

		console.add("找到命令文件: " + actualPath);
		console.add("文件内容预览:\n" + (fileContent.length() > 200 ? fileContent.substring(0, 200) + "..." : fileContent));

		// 解析命令（按行分割）
		String[] lines = fileContent.split("\n");
		List<String> commands = new ArrayList<>();
		for (String line : lines) {
			line = line.trim();
			if (!line.isEmpty() && !line.startsWith("#")) {
				commands.add(line);
			}
		}

		if (commands.isEmpty()) {
			console.add("命令文件没有有效的命令（空或全是注释）");
			return;
		}

		console.add("共 " + commands.size() + " 条有效命令，点击下方按钮执行");
		binding.install.setText("执行自定义命令");
		binding.install.setVisibility(View.VISIBLE);
		binding.install.setOnClickListener(v -> {
			binding.install.setEnabled(false);
			console.add(">>> 开始执行命令 <<<");
			// 使用 shell 执行命令列表
			shell.newJob().add(commands.toArray(new String[0])).to(console).submit(result -> {
				if (result.isSuccess()) {
					console.add(">>> 所有命令执行成功 <<<");
				} else {
					console.add(">>> 部分命令执行失败，退出码：" + result.getCode());
					// 如果有错误输出，也打印出来
					if (!result.getErr().isEmpty()) {
						console.add("错误输出:");
						for (String err : result.getErr()) console.add("  " + err);
					}
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
