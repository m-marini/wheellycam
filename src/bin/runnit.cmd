@echo off
rem Batch file to run on Windows

rem Remove "rem" from following two lines, if you'd like to use j2sdk.
rem set JAVA_HOME=...
rem set PATH=%JAVA_HOME%\bin

echo
java -version > nul
IF ERRORLEVEL 2 goto noJavaw
javaw > nul
IF ERRORLEVEL 2 goto noJavaw

java -cp ${opencv.jarFile};lib/${pom.build.finalName}.jar ${jar.mainClass} %1 %2

goto end

:noJavaw
echo.
echo Failed to run java.
echo Java runtime environment is required to run the application.
echo Setup Java environment at first.
echo.
echo The java command should be in PATH system environment variable.
echo.
echo If you would like to run java in your specified folder, you can edit runnit.bat
echo setting your JAVA_HOME as followings.
echo     before:
echo       rem set JAVA_HOME=...
echo       rem set PATH=%%JAVA_HOME%%\bin
echo     after:
echo       set JAVA_HOME=...
echo       set PATH=%%JAVA_HOME%%\bin
echo.
pause
goto end

:end