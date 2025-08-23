@ECHO OFF
REM >>> AUTOGEN: WRAPPER SCRIPT BEGIN
SET DIR=%~dp0
SET CLASSPATH=%DIR%gradle\wrapper\gradle-wrapper.jar
SET JAVA_EXE=java
"%JAVA_EXE%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
REM <<< AUTOGEN: WRAPPER SCRIPT END
