#!/bin/sh
# Gradle wrapper (Gradle 8.7). Generado manualmente porque en esta VM el
# daemon de Gradle no puede comunicarse con su cliente (ver README).
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -n "$JAVA_HOME" ] ; then JAVACMD="$JAVA_HOME/bin/java" ; else JAVACMD="java" ; fi
exec "$JAVACMD" "-Xmx64m" "-Xms64m" -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
