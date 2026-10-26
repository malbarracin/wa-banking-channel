@echo off
rem Ejecuta el wrapper del servicio desde la raiz del repositorio.
call "%~dp0services\wa-users-service\mvnw.cmd" -f "%~dp0services\wa-users-service\pom.xml" %*
exit /b %errorlevel%
