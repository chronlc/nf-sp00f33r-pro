#!/bin/bash
echo "=== FORCE BUILD SCRIPT ==="
echo "Setting JAVA_HOME to Java 17..."
export JAVA_HOME=/opt/openjdk-bin-17/
export PATH=$JAVA_HOME/bin:$PATH
echo "Java version: $(java -version 2>&1 | head -1)"
echo "Gradle version: $(./gradlew --version | head -5)"
echo "Starting clean build..."
./gradlew clean
echo "Starting assembleDebug..."
./gradlew assembleDebug --stacktrace --info
echo "Build completed!"
ls -la build/outputs/apk/debug/
