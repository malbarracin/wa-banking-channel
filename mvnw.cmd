@echo off
setlocal
pushd "%~dp0services\wa-users-service" || exit /b 1
call mvnw.cmd %*
set "BUILD_EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %BUILD_EXIT_CODE%
