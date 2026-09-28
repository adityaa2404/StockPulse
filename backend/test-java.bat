@echo off
echo Testing Java compilation...
javac -version
if %ERRORLEVEL% EQU 0 (
    echo Java compiler is available
) else (
    echo Java compiler is not available in PATH
)