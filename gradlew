#!/usr/bin/env sh

##############################################################################
# Gradle start script
##############################################################################

DIR="$(cd "$(dirname "$0")" && pwd)"
APP_HOME="$DIR"
DEFAULT_JVM_OPTS=""

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

JAVA_EXE=java

exec "$JAVA_EXE" $DEFAULT_JVM_OPTS -cp "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
