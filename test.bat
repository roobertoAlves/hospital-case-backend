@echo off
setlocal
cd /d "%~dp0"
if exist build rmdir /s /q build
mkdir build\classes build\test-classes

dir /s /b src\main\java\*.java > build\sources.txt
javac --release 21 -encoding UTF-8 -nowarn -d build\classes -cp "lib\compile\*;src\main\webapp\WEB-INF\lib\*" @build\sources.txt
if errorlevel 1 exit /b 1

dir /s /b src\test\java\*.java tools\tests\*.java > build\tests.txt
javac --release 21 -encoding UTF-8 -nowarn -d build\test-classes -cp "build\classes;lib\compile\*;lib\test\*;src\main\webapp\WEB-INF\lib\*" @build\tests.txt
if errorlevel 1 exit /b 1

java -cp "build\classes;build\test-classes;lib\compile\*;lib\test\*;src\main\webapp\WEB-INF\lib\*" support.RunTests %1
