@echo off
REM Runs SnackVar. Needs Java 17 or newer, but does not need administrator
REM rights: a per-user JDK is found automatically in the usual places.
setlocal EnableDelayedExpansion

set "ROOT=%~dp0"
set "JAR=%ROOT%target\snackvar.jar"
set "JAVA="

REM 1. A runtime bundled next to this script (the portable download).
if exist "%ROOT%jdk\bin\java.exe" set "JAVA=%ROOT%jdk\bin\java.exe"

REM 2. JAVA_HOME.
if not defined JAVA if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"

REM 3. On PATH.
if not defined JAVA for /f "delims=" %%J in ('where java 2^>nul') do if not defined JAVA set "JAVA=%%J"

REM 4. Per-user installs that need no administrator rights.
if not defined JAVA for /d %%D in ("%LOCALAPPDATA%\Programs\Eclipse Adoptium\*") do (
    if exist "%%D\bin\java.exe" if not defined JAVA set "JAVA=%%D\bin\java.exe"
)
if not defined JAVA for /d %%D in ("%USERPROFILE%\.jdks\*") do (
    if exist "%%D\bin\java.exe" if not defined JAVA set "JAVA=%%D\bin\java.exe"
)
if not defined JAVA for /d %%D in ("%LOCALAPPDATA%\Programs\Microsoft\jdk*") do (
    if exist "%%D\bin\java.exe" if not defined JAVA set "JAVA=%%D\bin\java.exe"
)

if not defined JAVA (
    echo.
    echo SnackVar needs Java 17 or newer, and none was found.
    echo.
    echo No administrator rights are required. Download the Windows .zip
    echo from https://adoptium.net/temurin/releases/ , extract it, and either:
    echo.
    echo   - set JAVA_HOME to the extracted folder, or
    echo   - rename the extracted folder to "jdk" and put it next to this script
    echo.
    pause
    exit /b 1
)

if not exist "%JAR%" (
    echo Building SnackVar...
    pushd "%ROOT%"
    REM The Maven wrapper downloads Maven itself; nothing to install.
    call "%ROOT%mvnw.cmd" -DskipTests -Djavafx.platform=win package
    set "BUILD_STATUS=!ERRORLEVEL!"
    popd
    if not "!BUILD_STATUS!"=="0" (
        echo.
        echo Build failed.
        pause
        exit /b 1
    )
)

"%JAVA%" -jar "%JAR%" %*
