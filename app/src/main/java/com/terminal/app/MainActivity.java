package com.terminal.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.KeyEvent;
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
    private ExecutorService executor;
    private Handler mainHandler;
    private Process shellProcess;
    private DataOutputStream shellOutputStream;
    private BufferedReader shellReader;
    private String currentDirectory = "/";
    private StringBuilder currentHistory = new StringBuilder();
    private boolean isRoot = false;
    
    private static final int COLOR_WHITE = Color.parseColor("#FFFFFF");
    private static final int COLOR_GREEN = Color.parseColor("#00FF00");
    private static final int COLOR_RED = Color.parseColor("#FF0000");
    private static final int COLOR_YELLOW = Color.parseColor("#FFFF00");
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // إنشاء الواجهة الرئيسية
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.BLACK);
        mainLayout.setPadding(15, 30, 15, 15);
        
        // منطقة عرض المخرجات
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
        updatePrompt(prompt);
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
        
        // فتح لوحة المفاتيح تلقائياً
        showKeyboard();
        
        // بدء Shell
        startShell();
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
    
    private void showKeyboard() {
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
                        appendToTerminal("Shell error: " + e.getMessage() + "\n");
                    });
                }
            });
        } catch (Exception e) {
            appendToTerminal("Cannot start system shell.\n");
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
        
        // حفظ في التاريخ
        currentHistory.append(command).append("\n");
        commandInput.setText("");
        
        // معالجة الأوامر المدمجة
        if (command.equals("clear")) {
            clearTerminal();
        } else if (command.equals("help")) {
            showHelp();
        } else if (command.equals("ls")) {
            executeLs();
        } else if (command.equals("pwd")) {
            appendToTerminal(currentDirectory + "\n");
        } else if (command.equals("su")) {
            executeSu(prompt);
        } else if (command.startsWith("chroot-distro")) {
            executeChrootDistro(command);
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
                appendToTerminal("Error executing command\n");
            }
        } else {
            appendToTerminal("Command not found: " + command + "\n");
        }
        
        // تحديث المؤشر
        updatePrompt(prompt);
        appendToTerminal(isRoot ? "# " : "$ ");
        
        // إعادة فتح لوحة المفاتيح
        showKeyboard();
    }
    
    private void executeSu(TextView prompt) {
        if (!isRoot) {
            appendToTerminal("Requesting root access...\n");
            try {
                Process suProcess = Runtime.getRuntime().exec(new String[]{"su"});
                DataOutputStream suOut = new DataOutputStream(suProcess.getOutputStream());
                BufferedReader suReader = new BufferedReader(new InputStreamReader(suProcess.getInputStream()));
                
                suOut.writeBytes("id\n");
                suOut.flush();
                
                String line = suReader.readLine();
                if (line != null && line.contains("uid=0")) {
                    isRoot = true;
                    appendToTerminal("✓ Root access granted!\n");
                    // إعادة تشغيل Shell مع صلاحيات root
                    if (shellProcess != null) {
                        shellProcess.destroy();
                    }
                    shellProcess = Runtime.getRuntime().exec(new String[]{"su"});
                    shellOutputStream = new DataOutputStream(shellProcess.getOutputStream());
                    shellReader = new BufferedReader(new InputStreamReader(shellProcess.getInputStream()));
                    
                    // قراءة المخرجات
                    executor.execute(() -> {
                        try {
                            String line2;
                            while ((line2 = shellReader.readLine()) != null) {
                                final String output = line2;
                                mainHandler.post(() -> appendToTerminal(output + "\n"));
                            }
                        } catch (Exception e) {}
                    });
                } else {
                    appendToTerminal("✗ Root access denied!\n");
                }
                suProcess.destroy();
            } catch (Exception e) {
                appendToTerminal("✗ Root access failed: " + e.getMessage() + "\n");
            }
        } else {
            appendToTerminal("Already have root access!\n");
        }
        updatePrompt(prompt);
    }
    
    private void executeChrootDistro(String command) {
        appendToTerminal("Starting Ubuntu chroot environment...\n");
        appendToTerminal("This may take a moment...\n\n");
        
        try {
            String[] cmd;
            if (isRoot) {
                cmd = new String[]{"su", "-c", command};
            } else {
                cmd = new String[]{"sh", "-c", command};
            }
            
            Process process = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            
            String line;
            while ((line = reader.readLine()) != null) {
                appendToTerminal(line + "\n");
            }
            while ((line = errorReader.readLine()) != null) {
                appendToTerminal(line + "\n");
            }
            
            process.waitFor();
            appendToTerminal("\n✓ Ubuntu session ended\n");
        } catch (Exception e) {
            appendToTerminal("Error: " + e.getMessage() + "\n");
            appendToTerminal("Make sure chroot-distro is installed and Ubuntu is set up.\n");
        }
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
                appendToTerminal(output.toString());
            } else {
                appendToTerminal("Cannot read directory\n");
            }
        } catch (Exception e) {
            appendToTerminal("Error: " + e.getMessage() + "\n");
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
        appendToTerminal("  help           - Show this help\n");
        appendToTerminal("  clear          - Clear screen\n");
        appendToTerminal("  ls             - List files\n");
        appendToTerminal("  cd <path>      - Change directory\n");
        appendToTerminal("  pwd            - Show current path\n");
        appendToTerminal("  su             - Switch to root (requires root)\n");
        appendToTerminal("  chroot-distro login ubuntu - Start Ubuntu\n");
        appendToTerminal("  echo <text>    - Print text\n");
        appendToTerminal("  exit           - Close app\n");
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
