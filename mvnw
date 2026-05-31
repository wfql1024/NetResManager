#!/bin/sh
# Maven Wrapper startup script, version 3.2.0

# Determine the project base directory
PRG="$0"
while [ -h "$PRG" ]; do
    ls=$(ls -ld "$PRG")
    link=$(expr "$ls" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=$(dirname "$PRG")/"$link"
    fi
done
SAVED="$(pwd)"
cd "$(dirname "$PRG")/" >/dev/null
APP_DIR="$(pwd -P)"
cd "$SAVED" >/dev/null

MAVEN_HOME="$APP_DIR/.mvn/wrapper/maven-home"
if [ ! -d "$MAVEN_HOME" ]; then
    mkdir -p "$MAVEN_HOME"
fi

WRAPPER_JAR="$APP_DIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_PROPERTIES="$APP_DIR/.mvn/wrapper/maven-wrapper.properties"

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading Maven Wrapper JAR..."
    if command -v curl > /dev/null 2>&1; then
        curl -L -o "$WRAPPER_JAR" "https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
    elif command -v wget > /dev/null 2>&1; then
        wget -O "$WRAPPER_JAR" "https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
    fi
fi

# Extract distribution URL from properties
DIST_URL=$(grep "distributionUrl" "$WRAPPER_PROPERTIES" | cut -d'=' -f2 | tr -d '[:space:]')

if [ ! -f "$MAVEN_HOME/bin/mvn" ]; then
    echo "Downloading Maven distribution..."
    DIST_ZIP="$MAVEN_HOME/maven.zip"
    if command -v curl > /dev/null 2>&1; then
        curl -L -o "$DIST_ZIP" "$DIST_URL"
    elif command -v wget > /dev/null 2>&1; then
        wget -O "$DIST_ZIP" "$DIST_URL"
    fi

    if [ -f "$DIST_ZIP" ]; then
        unzip -qo "$DIST_ZIP" -d "$MAVEN_HOME"
        rm -f "$DIST_ZIP"

        # Move contents of subdirectory up one level
        for d in "$MAVEN_HOME"/*/; do
            if [ -f "${d}bin/mvn" ]; then
                mv "$d"* "$MAVEN_HOME/" 2>/dev/null
                rmdir "$d" 2>/dev/null
                break
            fi
        done
    fi
fi

export M2_HOME="$MAVEN_HOME"
export JAVA_HOME="${JAVA_HOME:-$(dirname $(dirname $(readlink -f $(which java 2>/dev/null) 2>/dev/null) 2>/dev/null) 2>/dev/null)}"

exec "$MAVEN_HOME/bin/mvn" "$@"
