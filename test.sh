#!/usr/bin/env bash
# Compiles the project and runs every JUnit 5 test without Maven or an IDE (Linux, macOS, Git Bash).
# Requires JDK 21 on the PATH (java -version must show 21) and MySQL/MariaDB running.
# Usage: ./test.sh           (all)
#        ./test.sh models    (a single package)
set -e
cd "$(dirname "$0")"
SEP=":"
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; esac
LIBS="lib/compile/*${SEP}src/main/webapp/WEB-INF/lib/*"

rm -rf build && mkdir -p build/classes build/test-classes
find src/main/java -name "*.java" > build/sources.txt
javac --release 21 -encoding UTF-8 -nowarn -d build/classes -cp "$LIBS" @build/sources.txt
find src/test/java tools/tests -name "*.java" > build/tests.txt
javac --release 21 -encoding UTF-8 -nowarn -d build/test-classes -cp "build/classes${SEP}lib/test/*${SEP}$LIBS" @build/tests.txt
java -cp "build/classes${SEP}build/test-classes${SEP}lib/test/*${SEP}$LIBS" support.RunTests "$@"
