@echo off
REM Runs SnackVar. Requires Java 17 or newer on PATH.
setlocal

set "ROOT=%~dp0"
set "JAR=%ROOT%target\snackvar.jar"

where java >nul 2>nul
if errorlevel 1 (
    echo Java was not found on PATH. SnackVar needs Java 17 or newer.
    echo https://adoptium.net/temurin/releases/
    pause
    exit /b 1
)

if not exist "%JAR%" (
    echo Building SnackVar...
    where mvn >nul 2>nul
    if errorlevel 1 (
        echo No build of SnackVar found and Maven is not installed.
        echo Install Maven, or download a prebuilt snackvar.jar.
        pause
        exit /b 1
    )
    pushd "%ROOT%"
    call mvn -q -DskipTests package
    popd
)

java -jar "%JAR%" %*
