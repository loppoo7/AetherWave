@echo off
setlocal
set GRADLE_VERSION=9.5.0
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set DIST_ROOT=%GRADLE_USER_HOME%\wrapper\dists\gradle-%GRADLE_VERSION%-bin\aetherwave
set GRADLE_BIN=%DIST_ROOT%\gradle-%GRADLE_VERSION%\bin\gradle.bat
if not exist "%GRADLE_BIN%" (
  if not exist "%DIST_ROOT%" mkdir "%DIST_ROOT%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%DIST_ROOT%\gradle.zip'; Expand-Archive -Path '%DIST_ROOT%\gradle.zip' -DestinationPath '%DIST_ROOT%' -Force; Remove-Item '%DIST_ROOT%\gradle.zip'"
  if errorlevel 1 exit /b 1
)
call "%GRADLE_BIN%" %*
endlocal
