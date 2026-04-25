@echo off
echo ============================================
echo  SpringHi.ai - Stopping All Services
echo ============================================

echo.
echo Stopping backend (port 8080)...
for /f "tokens=5" %%i in ('netstat -aon ^| findstr ":8080 " ^| findstr "LISTENING"') do (
    taskkill /pid %%i /f >nul 2>&1
)

echo Stopping portfolio-service (port 8081)...
for /f "tokens=5" %%i in ('netstat -aon ^| findstr ":8081 " ^| findstr "LISTENING"') do (
    taskkill /pid %%i /f >nul 2>&1
)

echo Stopping gateway (port 9000)...
for /f "tokens=5" %%i in ('netstat -aon ^| findstr ":9000 " ^| findstr "LISTENING"') do (
    taskkill /pid %%i /f >nul 2>&1
)

echo Stopping frontend dev server (port 5173)...
for /f "tokens=5" %%i in ('netstat -aon ^| findstr ":5173 " ^| findstr "LISTENING"') do (
    taskkill /pid %%i /f >nul 2>&1
)

echo Closing named console windows...
taskkill /fi "windowtitle eq backend*" /f >nul 2>&1
taskkill /fi "windowtitle eq portfolio-service*" /f >nul 2>&1
taskkill /fi "windowtitle eq gateway*" /f >nul 2>&1
taskkill /fi "windowtitle eq frontend*" /f >nul 2>&1

echo.
echo ============================================
echo  All services stopped.
echo ============================================
