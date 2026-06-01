@if "%DEBUG%" == "" @echo off
setlocal
set APP_HOME=%dp0..
set WRAPPER_JAR="%APP_HOME%\gradle\wrapper\gradle-wrapper.jar"
"%JAVA_HOME%\bin\java" -classpath %WRAPPER_JAR% org.gradle.wrapper.GradleWrapperMain %*
endlocal
