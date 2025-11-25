@echo off
set DIR=%~dp0
set APP_HOME=%DIR%
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

set JAVA_EXE=java

"%JAVA_EXE%" -cp "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
