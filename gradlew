#!/bin/sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ]; then
  JAVA_EXE=$JAVA_HOME/bin/java
else
  JAVA_EXE=java
fi

if [ ! -x "$JAVA_EXE" ]; then
  echo "ERROR: Java executable not found." >&2
  exit 1
fi

exec "$JAVA_EXE" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
