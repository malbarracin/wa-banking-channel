@echo off
setlocal
set "MAVEN_VERSION=3.9.9"
if not defined MAVEN_USER_HOME set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
set "MAVEN_DIR=%MAVEN_USER_HOME%\wrapper\dists\apache-maven-%MAVEN_VERSION%\apache-maven-%MAVEN_VERSION%"
if not exist "%MAVEN_DIR%\bin\mvn.cmd" (
    powershell -NoProfile -Command "$ErrorActionPreference='Stop'; $d=$env:MAVEN_DIR; New-Item -ItemType Directory -Force -Path $d | Out-Null; $z=Join-Path $d 'maven.zip'; Invoke-WebRequest -UseBasicParsing ('https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/{0}/apache-maven-{0}-bin.zip' -f $env:MAVEN_VERSION) -OutFile $z; Expand-Archive -Force $z $d; Get-ChildItem (Join-Path $d ('apache-maven-'+$env:MAVEN_VERSION)) | Move-Item -Destination $d -Force; Remove-Item $z"
    if errorlevel 1 exit /b 1
)
call "%MAVEN_DIR%\bin\mvn.cmd" -f "%~dp0pom.xml" %*
exit /b %ERRORLEVEL%
