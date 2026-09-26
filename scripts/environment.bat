@echo off

if not defined SPRING_PROFILES_ACTIVE set "SPRING_PROFILES_ACTIVE=local"
if not defined SPRING_DATASOURCE_URL set "SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/postf1?currentSchema=springhi"
if not defined SPRING_DATASOURCE_USERNAME set "SPRING_DATASOURCE_USERNAME=postgres"
if not defined MAIL_FROM set "MAIL_FROM=info@springhi.ai"
if not defined SENDGRID_LIVE_TEST set "SENDGRID_LIVE_TEST=false"
if not defined BACKEND_URL set "BACKEND_URL=http://localhost:8080"
if not defined PORTFOLIO_URL set "PORTFOLIO_URL=http://localhost:8081"
if not defined ALLOWED_ORIGIN set "ALLOWED_ORIGIN=http://localhost:5173"
if not defined APP_REFERRAL_BASE_URL set "APP_REFERRAL_BASE_URL=http://localhost:5173"
if not defined VITE_API_BASE_URL set "VITE_API_BASE_URL=http://localhost:9000"
if not defined VITE_GOOGLE_CLIENT_ID if defined GOOGLE_CLIENT_ID set "VITE_GOOGLE_CLIENT_ID=%GOOGLE_CLIENT_ID%"

if not defined SENDGRID_API_KEY (
    echo ERROR: Set SENDGRID_API_KEY in scripts\local_environment.bat or the process environment.
    exit /b 1
)
if /I "%SENDGRID_API_KEY%"=="REPLACE_WITH_ROTATED_SENDGRID_API_KEY" (
    echo ERROR: Replace the SENDGRID_API_KEY placeholder in scripts\local_environment.bat.
    exit /b 1
)

exit /b 0
