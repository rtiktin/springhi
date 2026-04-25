@echo off
setlocal

set JAVA_HOME=C:\Program Files\Java\jdk-25.0.2
set PATH=%JAVA_HOME%\bin;%PATH%
set ROOT=C:\Users\rosst\IdeaProjects\demo-web

echo ============================================
echo  SpringHi.ai - Starting All Services
echo ============================================

echo.
echo [1/4] Building backend...
cd /d "%ROOT%\backend"
call mvn package -q -DskipTests
if errorlevel 1 ( echo ERROR: backend build failed & goto :error )

echo [2/4] Building portfolio-service...
cd /d "%ROOT%\portfolio-service"
call mvn package -q -DskipTests
if errorlevel 1 ( echo ERROR: portfolio-service build failed & goto :error )

echo [3/4] Building gateway...
cd /d "%ROOT%\gateway"
call mvn package -q -DskipTests
if errorlevel 1 ( echo ERROR: gateway build failed & goto :error )

echo.
echo Starting backend (port 8080)...
cd /d "%ROOT%\backend"
start "backend" cmd /k "java -jar target\demo-web-0.0.1-SNAPSHOT.jar"

echo Waiting for backend to start...
timeout /t 10 /nobreak >nul

echo Starting portfolio-service (port 8081)...
cd /d "%ROOT%\portfolio-service"
start "portfolio-service" cmd /k "java -jar target\portfolio-service-0.0.1-SNAPSHOT.jar"

echo Waiting for portfolio-service to start...
timeout /t 10 /nobreak >nul

echo Starting gateway (port 9000)...
cd /d "%ROOT%\gateway"
start "gateway" cmd /k "java -jar target\gateway-0.0.1-SNAPSHOT.jar"

echo Waiting for gateway to start...
timeout /t 8 /nobreak >nul

echo Starting frontend (port 5173)...
cd /d "%ROOT%\frontend"
start "frontend" cmd /k "npm run dev"

echo.
echo ============================================
echo  All services started:
echo    Backend:           http://localhost:8080
echo    Portfolio Service: http://localhost:8081
echo    Gateway:           http://localhost:9000
echo    Frontend:          http://localhost:5173
echo ============================================
goto :end

:error
echo.
echo Startup aborted due to build error.
exit /b 1

:end
endlocal
