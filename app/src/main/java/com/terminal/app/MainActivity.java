package com.terminal.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.Context;

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
    private LinearLayout mainLayout;
    private ExecutorService executor;
    private Handler mainHandler;
    private Process shellProcess;
    private DataOutputStream shellOutputStream;
    private BufferedReader shellReader;
    private String currentDirectory = "/";
    private boolean isRoot = false;
    
    private static final int COLOR_WHITE = Color.parseColor("#FFFFFF");
    private static final int COLOR_GREEN = Color.parseColor("#00FF00");
    private static final int COLOR_RED = Color.parseColor("#FF0000");
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // الواجهة الرئيسية
        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.BLACK);
        mainLayout.setPadding(15, 30, 15, 15);
        
        // منطقة عرض المخرجات (قابلة للتمرير)
        scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.BLACK);
        scrollView.setVerticalScrollBarEnabled(true);
        
        terminalOutput = new TextView(this);
        terminalOutput.setTextColor(COLOR_WHITE);
        terminalOutput.setTextSize(14);
        terminalOutput.setTypeface(Typeface.MONOSPACE);
        terminalOutput.setMovementMethod(new ScrollingMovementMethod());
        terminalOutput.setPadding(10, 10, 10, 10);
        terminalOutput.setTextIsSelectable(true);
        terminalOutput.setText("");
        
        // سطر الإدخال
        LinearLayout inputLayout = new LinearLayout(this);
        inputLayout.setOrientation(LinearLayout.HORIZONTAL);
        inputLayout.setBackgroundColor(Color.BLACK);
        inputLayout.setPadding(5, 10, 5, 10);
        
        TextView prompt = new TextView(this);
        prompt.setText("$ ");
        prompt.setTextColor(COLOR_GREEN);
        prompt.setTextSize(14);
        prompt.setTypeface(Typeface.MONOSPACE);
        
        commandInput = new EditText(this);
        commandInput.setBackgroundColor(Color.TRANSPARENT);
        commandInput.setTextColor(COLOR_WHITE);
        commandInput.setTextSize(14);
        commandInput.setTypeface(Typeface.MONOSPACE);
        commandInput.setHint("");
        commandInput.setSingleLine(true);
        commandInput.setFocusable(true);
        commandInput.setFocusableInTouchMode(true);
        commandInput.setCursorVisible(true);
        
        commandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || 
                (event != null && event.getAction() == KeyEvent.ACTION_DOWN && 
                 event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                executeCommand(prompt);
                return true;
            }
            return false;
        });
        
        inputLayout.addView(prompt);
        inputLayout.addView(commandInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        
        mainLayout.addView(scrollView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        mainLayout.addView(inputLayout);
        
        scrollView.addView(terminalOutput);
        
        setContentView(mainLayout);
        
        // عرض المؤشر
        appendToTerminal("$ ");
        
        // عند الضغط على أي مكان في الشاشة، تفتح لوحة المفاتيح
        mainLayout.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                openKeyboard();
                return true;
            }
            return false;
        });
        
        // فتح لوحة المفاتيح عند بدء التشغيل
        openKeyboard();
        
        // بدء Shell
        startShell();
    }
    
    private void openKeyboard() {
        commandInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(commandInput, InputMethodManager.SHOW_IMPLICIT);
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
                        mainHandler.post(() -> {
                            appendToTerminal(output + "\n");
                        });
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        appendToTerminal("Shell error\n");
                    });
                }
            });
        } catch (Exception e) {
            appendToTerminal("Cannot start shell\n");
        }
    }
    
    private void executeCommand(TextView prompt) {
        String command = commandInput.getText().toString().trim();
        if (command.isEmpty()) {
            commandInput.setText("");
            return;
        }
        
        // عرض الأمر
        appendToTerminal(command + "\n");
        
        commandInput.setText("");
        
        // معالجة الأوامر
        if (command.equals("clear")) {
            clearTerminal();
        } else if (command.equals("help")) {
            showHelp();
        } else if (command.equals("ls")) {
            executeLs();
        } else if (command.equals("pwd")) {
            appendToTerminal(currentDirectory + "\n");
        } else if (command.startsWith("cd ")) {
            changeDirectory(command.substring(3));
        } else if (command.startsWith("echo ")) {
            appendToTerminal(command.substring(5) + "\n");
        } else if (shellOutputStream != null) {
            try {
                shellOutputStream.writeBytes(command + "\n");
                shellOutputStream.flush();
                Thread.sleep(100);
            } catch (Exception e) {
                appendToTerminal("Error\n");
            }
        } else {
            appendToTerminal("Command not found\n");
        }
        
        // تحديث المؤشر
        updatePrompt(prompt);
        appendToTerminal(isRoot ? "# " : "$ ");
        
        // إعادة فتح لوحة المفاتيح
        openKeyboard();
    }
    
    private void updatePrompt(TextView prompt) {
        if (isRoot) {
            prompt.setText("# ");
            prompt.setTextColor(COLOR_RED);
        } else {
            prompt.setText("$ ");
            prompt.setTextColor(COLOR_GREEN);
        }
    }
    
    private void executeLs() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", "ls -la " + currentDirectory});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                appendToTerminal(line + "\n");
            }
            process.waitFor();
        } catch (Exception e) {
            appendToTerminal("Error: " + e.getMessage() + "\n");
        }
    }
    
    private void executeLsOld() {
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
                appendToTerminal(output.toString());
            } else {
                appendToTerminal("Cannot read directory\n");
            }
        } catch (Exception e) {
            appendToTerminal("Error\n");
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
        } else {
            appendToTerminal("Directory not found: " + path + "\n");
        }
    }
    
    private void showHelp() {
        appendToTerminal("\n=== Available Commands ===\n");
        appendToTerminal("  help  - Show this help\n");
        appendToTerminal("  clear - Clear screen\n");
        appendToTerminal("  ls    - List files\n");
        appendToTerminal("  cd    - Change directory\n");
        appendToTerminal("  pwd   - Show current path\n");
        appendToTerminal("  echo  - Print text\n");
        appendToTerminal("==========================\n");
    }
    
    private void clearTerminal() {
        mainHandler.post(() -> terminalOutput.setText(""));
    }
    
    private void appendToTerminal(String text) {
        mainHandler.post(() -> {
            terminalOutput.append(text);
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
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
