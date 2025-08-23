# >>> AUTOGEN: WRAPPER SCRIPT BEGIN
#!/usr/bin/env sh

APP_BASE_NAME="Gradle"
APP_HOME=$(cd "${0%/*}"; pwd)

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

JAVA_EXE="java"

exec "$JAVA_EXE" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
# <<< AUTOGEN: WRAPPER SCRIPT END
