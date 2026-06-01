#!/bin/sh
JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}
APP_HOME=$(cd "$(dirname "$0")" && pwd)
exec "$JAVA_HOME/bin/java" -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
