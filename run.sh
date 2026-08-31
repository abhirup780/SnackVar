#!/usr/bin/env bash
# Builds SnackVar if needed, then runs it.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$ROOT/target/snackvar.jar"

# Finds a Java 17+ runtime. Checks PATH first, then the usual places a JDK
# ends up on Linux and macOS, so this works without one being on PATH.
find_java() {
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        echo "$JAVA_HOME/bin/java"; return 0
    fi
    if command -v java >/dev/null 2>&1; then
        command -v java; return 0
    fi
    for candidate in \
        "$HOME"/.local/jdk-*/bin/java \
        "$HOME"/.sdkman/candidates/java/current/bin/java \
        /usr/lib/jvm/*/bin/java \
        /Library/Java/JavaVirtualMachines/*/Contents/Home/bin/java
    do
        [ -x "$candidate" ] && { echo "$candidate"; return 0; }
    done
    return 1
}

if ! JAVA="$(find_java)"; then
    cat >&2 <<'MSG'
error: no Java runtime found. SnackVar needs Java 17 or newer.

  Debian/Ubuntu : sudo apt install openjdk-21-jre
  Fedora        : sudo dnf install java-21-openjdk
  Arch          : sudo pacman -S jre-openjdk
  No root       : download a JDK from https://adoptium.net and unpack it
                  to ~/.local/ (this script looks in ~/.local/jdk-*)
MSG
    exit 1
fi

if [ ! -f "$JAR" ]; then
    echo "Building SnackVar…"
    JAVA_BIN_DIR="$(dirname "$JAVA")"
    export JAVA_HOME="$(dirname "$JAVA_BIN_DIR")"
    if [ -x "$ROOT/mvnw" ]; then
        "$ROOT/mvnw" -q -DskipTests package
    elif command -v mvn >/dev/null 2>&1; then
        (cd "$ROOT" && mvn -q -DskipTests package)
    else
        echo "error: no build of SnackVar found and Maven is not installed." >&2
        echo "       Install Maven, or use a prebuilt target/snackvar.jar." >&2
        exit 1
    fi
fi

exec "$JAVA" -jar "$JAR" "$@"
