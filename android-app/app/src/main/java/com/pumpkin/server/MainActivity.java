package com.pumpkin.server;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/** 极简控制界面：启动/停止服务端，查看控制台输出。 */
public class MainActivity extends Activity {

    private static final int REQ_NOTIFICATIONS = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView statusView;
    private TextView logView;
    private ScrollView scrollView;
    private EditText commandInput;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    private View buildUi() {
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (12 * density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        statusView = new TextView(this);
        statusView.setTextSize(13);
        statusView.setTypeface(Typeface.MONOSPACE);
        root.addView(statusView, matchWrap());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button start = new Button(this);
        start.setText("启动");
        Button stop = new Button(this);
        stop.setText("停止");
        Button clear = new Button(this);
        clear.setText("清屏");
        row.addView(start, weight());
        row.addView(stop, weight());
        row.addView(clear, weight());
        root.addView(row, matchWrap());

        Button battery = new Button(this);
        battery.setText("电池优化设置（建议设为不受限制）");
        root.addView(battery, matchWrap());

        Button dir = new Button(this);
        dir.setText("复制数据目录路径");
        root.addView(dir, matchWrap());

        LinearLayout cmdRow = new LinearLayout(this);
        cmdRow.setOrientation(LinearLayout.HORIZONTAL);
        commandInput = new EditText(this);
        commandInput.setHint("控制台命令：list / op 玩家名 / stop");
        commandInput.setInputType(InputType.TYPE_CLASS_TEXT);
        commandInput.setSingleLine(true);
        cmdRow.addView(commandInput, weight());
        Button send = new Button(this);
        send.setText("发送");
        cmdRow.addView(send);
        root.addView(cmdRow, matchWrap());

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommandFromInput();
            }
        });

        scrollView = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextSize(10);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        scrollView.addView(logView);
        LinearLayout.LayoutParams logParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        logParams.weight = 1f;
        root.addView(scrollView, logParams);

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startServer();
            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopServer();
            }
        });
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PumpkinServer.get().clearLog();
                refresh();
            }
        });
        battery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBatterySettings();
            }
        });
        dir.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyWorkDir();
            }
        });
        return root;
    }

    private void refresh() {
        PumpkinServer server = PumpkinServer.get();
        boolean running = server.isRunning();
        String dir = currentWorkDir();
        String state;
        if (running) {
            long secs = Math.max(0, (System.currentTimeMillis() - server.getStartedAt()) / 1000);
            state = "● 运行中 " + (secs / 60) + "分" + (secs % 60) + "秒";
        } else if (server.getExitCode() != Integer.MIN_VALUE) {
            state = "○ 已停止（上次退出码 " + server.getExitCode() + "）";
        } else {
            state = "○ 未启动";
        }
        String lan = PumpkinServer.findLanIpv4();
        String connect = (lan == null)
                ? "未检测到局域网 IP（确认已连上 WiFi）"
                : "Java 连 " + lan + ":25565　|　基岩连 " + lan + ":19132";
        statusView.setText(state + "\n目录: " + dir + "\n" + connect);

        String text = server.tailLog();
        if (!text.contentEquals(logView.getText())) {
            logView.setText(text);
            scrollView.post(new Runnable() {
                @Override
                public void run() {
                    scrollView.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }

    private void startServer() {
        requestNotificationPermissionIfNeeded();
        Intent intent = new Intent(this, ServerService.class);
        intent.setAction(ServerService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopServer() {
        Intent intent = new Intent(this, ServerService.class);
        intent.setAction(ServerService.ACTION_STOP);
        startService(intent);
    }

    private void sendCommandFromInput() {
        if (commandInput == null) {
            return;
        }
        String cmd = commandInput.getText().toString();
        if (cmd.trim().isEmpty()) {
            return;
        }
        PumpkinServer.get().sendCommand(cmd);
        commandInput.setText("");
        refresh();
    }

    private void copyWorkDir() {
        PumpkinServer server = PumpkinServer.get();
        String dir = server.getWorkDir() == null
                ? getFilesDir().getAbsolutePath()
                : server.getWorkDir().getAbsolutePath();
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("pumpkin dir", dir));
            Toast.makeText(this, "已复制: " + dir, Toast.LENGTH_LONG).show();
        }
    }

    private String currentWorkDir() {
        PumpkinServer server = PumpkinServer.get();
        if (server.getWorkDir() != null) {
            return server.getWorkDir().getAbsolutePath();
        }
        File external = getExternalFilesDir(null);
        return external != null ? external.getAbsolutePath() : getFilesDir().getAbsolutePath();
    }

    private void openBatterySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception first) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception second) {
                Toast.makeText(this, "无法打开电池优化设置，请手动到系统设置里关闭限制",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_NOTIFICATIONS);
            }
        }
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.weight = 1f;
        return params;
    }
}
