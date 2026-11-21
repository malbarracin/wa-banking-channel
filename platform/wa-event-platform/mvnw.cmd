@echo off
setlocal
set "BASE_DIR=%~dp0"
set "WRAPPER_JAR=%BASE_DIR%.mvn\wrapper\maven-wrapper.jar"
if not exist "%WRAPPER_JAR%" powershell -NoProfile -Command "$u = (Get-Content -LiteralPath '%BASE_DIR%.mvn\wrapper\maven-wrapper.properties' | Where-Object { $_ -like 'wrapperUrl=*' }) -replace '^wrapperUrl=', ''; Invoke-WebRequest -UseBasicParsing -Uri $u -OutFile '%WRAPPER_JAR%'"
if not exist "%WRAPPER_JAR%" exit /b 1
set "JAVA_EXE=java"
if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
"%JAVA_EXE%" "-Dmaven.multiModuleProjectDirectory=%BASE_DIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
exit /b %ERRORLEVEL%
