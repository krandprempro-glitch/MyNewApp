package com.terminal.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
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
    private ScrollView scrollView;
    private LinearLayout mainLayout;
    private ExecutorService executor;
    private Handler mainHandler;
    private Process shellProcess;
    private DataOutputStream shellOutputStream;
    private BufferedReader shellReader;
    private String currentDirectory = "/";
    private StringBuilder currentCommand = new StringBuilder();
    private boolean isRoot = false;
    private boolean waitingForCommand = true;
    
    private static final int COLOR_WHITE = Color.parseColor("#FFFFFF");
    private static final int COLOR_GREEN = Color.parseColor("#00FF00");
    private static final int COLOR_RED = Color.parseColor("#FF0000");
    private static final int COLOR_CYAN = Color.parseColor("#00FFFF");
    
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
        terminalOutput.setFocusable(true);
        terminalOutput.setFocusableInTouchMode(true);
        terminalOutput.setClickable(true);
        
        // معالجة الكتابة مباشرة على TextView
        terminalOutput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                char unicodeChar = event.getUnicodeChar();
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    executeCommand();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DEL) {
                    if (currentCommand.length() > 0) {
                        currentCommand.deleteCharAt(currentCommand.length() - 1);
                        updateTerminalLine();
                    }
                    return true;
                } else if (unicodeChar != 0 && waitingForCommand) {
                    currentCommand.append((char) unicodeChar);
                    updateTerminalLine();
                    return true;
                }
            }
            return false;
        });
        
        terminalOutput.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                openKeyboard();
                return true;
            }
            return false;
        });
        
        mainLayout.addView(scrollView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        
        scrollView.addView(terminalOutput);
        
        setContentView(mainLayout);
        
        // عرض المؤشر الأولي
        appendToTerminal(isRoot ? "# " : "$ ");
        
        // فتح لوحة المفاتيح
        openKeyboard();
        
        // بدء Shell
        startShell();
    }
    
    private void updateTerminalLine() {
        String text = terminalOutput.getText().toString();
        int lastIndex = text.lastIndexOf(isRoot ? "# " : "$ ");
        if (lastIndex >= 0) {
            String newText = text.substring(0, lastIndex + 2) + currentCommand.toString();
            terminalOutput.setText(newText);
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
    }
    
    private void openKeyboard() {
        terminalOutput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(terminalOutput, InputMethodManager.SHOW_IMPLICIT);
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
                            if (waitingForCommand) {
                                waitingForCommand = false;
                            }
                            appendToTerminal(output + "\n");
                            waitingForCommand = true;
                            appendToTerminal(isRoot ? "# " : "$ ");
                            currentCommand.setLength(0);
                            openKeyboard();
                        });
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        appendToTerminal("Shell error\n");
                        appendToTerminal(isRoot ? "# " : "$ ");
                    });
                }
            });
        } catch (Exception e) {
            appendToTerminal("Cannot start shell\n");
            appendToTerminal(isRoot ? "# " : "$ ");
        }
    }
    
    private void executeCommand() {
        String command = currentCommand.toString().trim();
        if (command.isEmpty()) {
            currentCommand.setLength(0);
            appendToTerminal("\n" + (isRoot ? "# " : "$ "));
            return;
        }
        
        // حذف المؤقت من السطر الحالي وإضافة الأمر
        String text = terminalOutput.getText().toString();
        String newText = text.substring(0, text.length() - 2) + command + "\n";
        terminalOutput.setText(newText);
        
        waitingForCommand = false;
        
        // معالجة الأوامر المدمجة
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
        } else if (command.equals("su")) {
            executeSu();
        } else if (command.equals("exit") || command.equals("logout")) {
            if (isRoot) {
                isRoot = false;
                appendToTerminal("Exit root\n");
            } else {
                appendToTerminal("Goodbye!\n");
                finish();
                return;
            }
        } else if (shellOutputStream != null) {
            try {
                shellOutputStream.writeBytes(command + "\n");
                shellOutputStream.flush();
                Thread.sleep(100);
            } catch (Exception e) {
                appendToTerminal("Error\n");
            }
        } else {
            appendToTerminal("Command not found: " + command + "\n");
        }
        
        currentCommand.setLength(0);
        waitingForCommand = true;
        appendToTerminal(isRoot ? "# " : "$ ");
        openKeyboard();
    }
    
    private void executeSu() {
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
                    if (shellProcess != null) {
                        shellProcess.destroy();
                    }
                    shellProcess = Runtime.getRuntime().exec(new String[]{"su"});
                    shellOutputStream = new DataOutputStream(shellProcess.getOutputStream());
                    shellReader = new BufferedReader(new InputStreamReader(shellProcess.getInputStream()));
                    
                    executor.execute(() -> {
                        try {
                            String line2;
                            while ((line2 = shellReader.readLine()) != null) {
                                final String output = line2;
                                mainHandler.post(() -> {
                                    if (waitingForCommand) {
                                        waitingForCommand = false;
                                    }
                                    appendToTerminal(output + "\n");
                                    waitingForCommand = true;
                                    appendToTerminal(isRoot ? "# " : "$ ");
                                    currentCommand.setLength(0);
                                });
                            }
                        } catch (Exception e) {}
                    });
                } else {
                    appendToTerminal("✗ Root access denied!\n");
                }
                suProcess.destroy();
            } catch (Exception e) {
                appendToTerminal("✗ Root access failed\n");
            }
        } else {
            appendToTerminal("Already root\n");
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
        appendToTerminal("\n╔═══════════════════════════════════════════╗\n");
        appendToTerminal("║     Advanced Terminal Emulator            ║\n");
        appendToTerminal("╠═══════════════════════════════════════════╣\n");
        appendToTerminal("║  help  - Show this help                   ║\n");
        appendToTerminal("║  clear - Clear screen                     ║\n");
        appendToTerminal("║  ls    - List files                       ║\n");
        appendToTerminal("║  cd    - Change directory                 ║\n");
        appendToTerminal("║  pwd   - Show current path                ║\n");
        appendToTerminal("║  echo  - Print text                       ║\n");
        appendToTerminal("║  su    - Switch to root                   ║\n");
        appendToTerminal("║  exit  - Close app                        ║\n");
        appendToTerminal("╚═══════════════════════════════════════════╝\n");
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
