#!/usr/bin/env bash
# Compila o projeto e roda todos os testes JUnit 5 sem Maven e sem IDE (Linux, macOS, Git Bash).
# Requer JDK 21 no PATH (java -version deve mostrar 21) e o MySQL ligado.
# Uso: ./testar.sh            (todos)
#      ./testar.sh models     (so um pacote)
set -e
cd "$(dirname "$0")"
SEP=":"
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; esac
LIBS="lib/compile/*${SEP}src/main/webapp/WEB-INF/lib/*"

rm -rf build && mkdir -p build/classes build/test-classes
find src/main/java -name "*.java" > build/fontes.txt
javac --release 21 -encoding UTF-8 -nowarn -d build/classes -cp "$LIBS" @build/fontes.txt
find src/test/java ferramentas/testes -name "*.java" > build/testes.txt
javac --release 21 -encoding UTF-8 -nowarn -d build/test-classes -cp "build/classes${SEP}lib/test/*${SEP}$LIBS" @build/testes.txt
java -cp "build/classes${SEP}build/test-classes${SEP}lib/test/*${SEP}$LIBS" support.RodarTestes "$@"
