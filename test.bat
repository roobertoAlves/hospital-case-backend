@echo off
rem Compila o projeto e roda todos os testes JUnit 5 sem Maven e sem IDE (Windows).
rem Requer JDK 21 no PATH (java -version deve mostrar 21) e o MySQL ligado.
rem Uso: testar.bat            (todos)
rem      testar.bat models     (so um pacote)
setlocal
cd /d "%~dp0"
if exist build rmdir /s /q build
mkdir build\classes build\test-classes

dir /s /b src\main\java\*.java > build\fontes.txt
javac --release 21 -encoding UTF-8 -nowarn -d build\classes -cp "lib\compile\*;src\main\webapp\WEB-INF\lib\*" @build\fontes.txt
if errorlevel 1 exit /b 1

rem O executor (ferramentas\testes) fica fora das pastas de codigo da IDE: ele usa o
rem junit-platform-launcher, que o Eclipse/IntelliJ ja trazem embutido para rodar os testes.
dir /s /b src\test\java\*.java ferramentas\testes\*.java > build\testes.txt
javac --release 21 -encoding UTF-8 -nowarn -d build\test-classes -cp "build\classes;lib\compile\*;lib\test\*;src\main\webapp\WEB-INF\lib\*" @build\testes.txt
if errorlevel 1 exit /b 1

java -cp "build\classes;build\test-classes;lib\compile\*;lib\test\*;src\main\webapp\WEB-INF\lib\*" support.RunTests %1
