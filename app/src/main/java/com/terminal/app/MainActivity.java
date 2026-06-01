package com.terminal.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    
    private TextView terminalOutput;
    private EditText commandInput;
    private ScrollView scrollView;
    private ExecutorService executor;
    private Handler mainHandler;
    private Process shellProcess;
    private DataOutputStream shellOutputStream;
    private BufferedReader shellReader;
    private String currentDirectory = "/data/data/com.termux/files/home";
    private StringBuilder currentHistory = new StringBuilder();
    private TextView statusBar;
    
    // الألوان
    private static final int COLOR_GREEN = Color.parseColor("#00FF00");
    private static final int COLOR_WHITE = Color.parseColor("#FFFFFF");
    private static final int COLOR_YELLOW = Color.parseColor("#FFFF00");
    private static final int COLOR_RED = Color.parseColor("#FF0000");
    private static final int COLOR_CYAN = Color.parseColor("#00FFFF");
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // إنشاء الواجهة الرئيسية
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.BLACK);
        mainLayout.setPadding(20, 30, 20, 20);
        
        // شريط الحالة
        statusBar = new TextView(this);
        statusBar.setText(" ⚡ Advanced Terminal v2.0 | " + currentDirectory);
        statusBar.setTextColor(COLOR_CYAN);
        statusBar.setBackgroundColor(Color.parseColor("#1A1A1A"));
        statusBar.setPadding(10, 15, 10, 15);
        statusBar.setTypeface(Typeface.MONOSPACE);
        statusBar.setTextSize(12);
        
        // منطقة عرض المخرجات
        scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.BLACK);
        scrollView.setVerticalScrollBarEnabled(true);
        
        terminalOutput = new TextView(this);
        terminalOutput.setTextColor(COLOR_GREEN);
        terminalOutput.setTextSize(14);
        terminalOutput.setTypeface(Typeface.MONOSPACE);
        terminalOutput.setMovementMethod(new ScrollingMovementMethod());
        terminalOutput.setPadding(10, 10, 10, 10);
        terminalOutput.setTextIsSelectable(true);
        terminalOutput.setText("");
        
        // سطر الإدخال
        LinearLayout inputLayout = new LinearLayout(this);
        inputLayout.setOrientation(LinearLayout.HORIZONTAL);
        inputLayout.setBackgroundColor(Color.parseColor("#1A1A1A"));
        inputLayout.setPadding(10, 15, 10, 15);
        
        TextView prompt = new TextView(this);
        prompt.setText("➜ ");
        prompt.setTextColor(COLOR_GREEN);
        prompt.setTextSize(14);
        prompt.setTypeface(Typeface.MONOSPACE);
        
        commandInput = new EditText(this);
        commandInput.setBackgroundColor(Color.TRANSPARENT);
        commandInput.setTextColor(COLOR_WHITE);
        commandInput.setTextSize(14);
        commandInput.setTypeface(Typeface.MONOSPACE);
        commandInput.setHint("اكتب الأمر هنا...");
        commandInput.setHintTextColor(Color.parseColor("#666666"));
        commandInput.setSingleLine(true);
        
        commandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || 
                (event != null && event.getAction() == KeyEvent.ACTION_DOWN && 
                 event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                executeCommand();
                return true;
            }
            return false;
        });
        
        inputLayout.addView(prompt);
        inputLayout.addView(commandInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        
        mainLayout.addView(statusBar);
        mainLayout.addView(scrollView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        mainLayout.addView(inputLayout);
        
        scrollView.addView(terminalOutput);
        
        setContentView(mainLayout);
        
        // عرض رسالة الترحيب
        showWelcomeMessage();
        
        // بدء Shell
        startShell();
    }
    
    private void showWelcomeMessage() {
        appendColoredText("╔═══════════════════════════════════════════╗\n", COLOR_CYAN);
        appendColoredText("║     Advanced Terminal Emulator v2.0       ║\n", COLOR_YELLOW);
        appendColoredText("╠═══════════════════════════════════════════╣\n", COLOR_CYAN);
        appendColoredText("║  الأوامر المتاحة:                         ║\n", COLOR_GREEN);
        appendColoredText("║  • help, clear, ls, cd, pwd               ║\n", COLOR_WHITE);
        appendColoredText("║  • mkdir, remove, cat, echo               ║\n", COLOR_WHITE);
        appendColoredText("║  • exit, history                          ║\n", COLOR_WHITE);
        appendColoredText("╚═══════════════════════════════════════════╝\n\n", COLOR_CYAN);
    }
    
    private void startShell() {
        try {
            shellProcess = Runtime.getRuntime().exec(new String[]{"/system/bin/sh"});
            shellOutputStream = new DataOutputStream(shellProcess.getOutputStream());
            shellReader = new BufferedReader(new InputStreamReader(shellProcess.getInputStream()));
            
            executor.execute(() -> {
                try {
                    String line;
                    while ((line = shellReader.readLine()) != null) {
                        final String output = line;
                        mainHandler.post(() -> appendColoredText(output + "\n", COLOR_WHITE));
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> appendColoredText("Shell error: " + e.getMessage() + "\n", COLOR_RED));
                }
            });
        } catch (Exception e) {
            appendColoredText("⚠️  Cannot start system shell. Using built-in commands only.\n", COLOR_YELLOW);
        }
    }
    
    private void executeCommand() {
        String command = commandInput.getText().toString().trim();
        if (command.isEmpty()) {
            commandInput.setText("");
            return;
        }
        
        // حفظ في التاريخ
        currentHistory.append(command).append("\n");
        
        appendColoredText("➜ " + command + "\n", COLOR_GREEN);
        commandInput.setText("");
        
        // معالجة الأوامر المدمجة
        if (command.equals("clear")) {
            clearTerminal();
        } else if (command.equals("help")) {
            showHelp();
        } else if (command.equals("ls")) {
            executeLs();
        } else if (command.equals("pwd")) {
            appendColoredText(currentDirectory + "\n", COLOR_CYAN);
        } else if (command.equals("history")) {
            appendColoredText("\n=== تاريخ الأوامر ===\n" + currentHistory.toString() + "\n", COLOR_YELLOW);
        } else if (command.equals("exit")) {
            appendColoredText("Goodbye!\n", COLOR_YELLOW);
            finish();
        } else if (command.startsWith("cd ")) {
            changeDirectory(command.substring(3));
        } else if (command.startsWith("echo ")) {
            String msg = command.substring(5);
            appendColoredText(msg + "\n", COLOR_WHITE);
        } else if (command.startsWith("mkdir ")) {
            createDirectory(command.substring(6));
        } else if (command.startsWith("remove ")) {
            removeFile(command.substring(7));
        } else if (command.startsWith("cat ")) {
            readFile(command.substring(4));
        } else if (command.equals("whoami")) {
            appendColoredText("user@android\n", COLOR_CYAN);
        } else if (command.equals("date")) {
            appendColoredText(java.time.LocalDateTime.now().toString() + "\n", COLOR_CYAN);
        } else if (command.startsWith("touch ")) {
            createFile(command.substring(6));
        } else if (shellOutputStream != null) {
            try {
                shellOutputStream.writeBytes(command + "\n");
                shellOutputStream.flush();
            } catch (Exception e) {
                appendColoredText("Command not found: " + command + "\n", COLOR_RED);
            }
        } else {
            appendColoredText("Command not found: " + command + "\n", COLOR_RED);
        }
        
        // تحديث شريط الحالة
        updateStatusBar();
    }
    
    private void executeLs() {
        try {
            File dir = new File(currentDirectory);
            File[] files = dir.listFiles();
            if (files != null) {
                StringBuilder output = new StringBuilder();
                for (File f : files) {
                    if (f.isDirectory()) {
                        output.append("📁 ").append(f.getName()).append("/\n");
                    } else {
                        output.append("📄 ").append(f.getName()).append("\n");
                    }
                }
                appendColoredText(output.toString(), COLOR_WHITE);
            } else {
                appendColoredText("Cannot read directory\n", COLOR_RED);
            }
        } catch (Exception e) {
            appendColoredText("Error: " + e.getMessage() + "\n", COLOR_RED);
        }
    }
    
    private void changeDirectory(String path) {
        File newDir;
        if (path.equals("..")) {
            newDir = new File(currentDirectory).getParentFile();
        } else if (path.equals("/")) {
            newDir = new File("/");
        } else if (path.startsWith("/")) {
            newDir = new File(path);
        } else {
            newDir = new File(currentDirectory, path);
        }
        
        if (newDir != null && newDir.exists() && newDir.isDirectory()) {
            currentDirectory = newDir.getAbsolutePath();
            appendColoredText("Changed to: " + currentDirectory + "\n", COLOR_CYAN);
        } else {
            appendColoredText("Directory not found: " + path + "\n", COLOR_RED);
        }
    }
    
    private void createDirectory(String path) {
        File dir = new File(currentDirectory, path);
        if (dir.mkdir()) {
            appendColoredText("Directory created: " + path + "\n", COLOR_GREEN);
        } else {
            appendColoredText("Failed to create directory\n", COLOR_RED);
        }
    }
    
    private void removeFile(String path) {
        File file = new File(currentDirectory, path);
        if (file.delete()) {
            appendColoredText("Deleted: " + path + "\n", COLOR_GREEN);
        } else {
            appendColoredText("Failed to delete: " + path + "\n", COLOR_RED);
        }
    }
    
    private void readFile(String path) {
        try {
            File file = new File(currentDirectory, path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(file)));
            String line;
            while ((line = reader.readLine()) != null) {
                appendColoredText(line + "\n", COLOR_WHITE);
            }
            reader.close();
        } catch (Exception e) {
            appendColoredText("Cannot read file: " + e.getMessage() + "\n", COLOR_RED);
        }
    }
    
    private void createFile(String path) {
        try {
            File file = new File(currentDirectory, path);
            if (file.createNewFile()) {
                appendColoredText("File created: " + path + "\n", COLOR_GREEN);
            } else {
                appendColoredText("File already exists\n", COLOR_YELLOW);
            }
        } catch (Exception e) {
            appendColoredText("Failed to create file\n", COLOR_RED);
        }
    }
    
    private void showHelp() {
        appendColoredText("\n═══════════════════════════════════════════\n", COLOR_CYAN);
        appendColoredText("📖  الأوامر المتاحة:\n\n", COLOR_YELLOW);
        appendColoredText("  📁  إدارة الملفات:\n", COLOR_GREEN);
        appendColoredText("    ls              - عرض الملفات والمجلدات\n", COLOR_WHITE);
        appendColoredText("    cd <path>       - تغيير المجلد\n", COLOR_WHITE);
        appendColoredText("    pwd             - عرض المسار الحالي\n", COLOR_WHITE);
        appendColoredText("    mkdir <name>    - إنشاء مجلد جديد\n", COLOR_WHITE);
        appendColoredText("    remove <file>   - حذف ملف\n", COLOR_WHITE);
        appendColoredText("    cat <file>      - عرض محتوى ملف\n", COLOR_WHITE);
        appendColoredText("    touch <file>    - إنشاء ملف جديد\n\n", COLOR_WHITE);
        
        appendColoredText("  🔧  أوامر النظام:\n", COLOR_GREEN);
        appendColoredText("    echo <text>     - طباعة نص\n", COLOR_WHITE);
        appendColoredText("    clear           - مسح الشاشة\n", COLOR_WHITE);
        appendColoredText("    history         - عرض تاريخ الأوامر\n", COLOR_WHITE);
        appendColoredText("    whoami          - عرض اسم المستخدم\n", COLOR_WHITE);
        appendColoredText("    date            - عرض التاريخ والوقت\n", COLOR_WHITE);
        appendColoredText("    help            - عرض هذه المساعدة\n", COLOR_WHITE);
        appendColoredText("    exit            - الخروج من التطبيق\n", COLOR_WHITE);
        appendColoredText("═══════════════════════════════════════════\n\n", COLOR_CYAN);
    }
    
    private void clearTerminal() {
        mainHandler.post(() -> terminalOutput.setText(""));
    }
    
    private void appendColoredText(String text, int color) {
        mainHandler.post(() -> {
            SpannableStringBuilder builder = new SpannableStringBuilder(terminalOutput.getText());
            int start = builder.length();
            builder.append(text);
            builder.setSpan(new ForegroundColorSpan(color), start, builder.length(), 0);
            terminalOutput.setText(builder);
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
    }
    
    private void updateStatusBar() {
        if (statusBar != null) {
            statusBar.setText(" ⚡ Advanced Terminal v2.0 | " + currentDirectory);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (shellOutputStream != null) shellOutputStream.close();
            if (shellReader != null) shellReader.close();
            if (shellProcess != null) shellProcess.destroy();
        } catch (Exception e) {}
    }
}
