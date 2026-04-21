@ECHO OFF
SETLOCAL

SET DIRNAME=%~dp0
IF "%DIRNAME%"=="" SET DIRNAME=.
SET APP_BASE_NAME=%~n0
SET APP_HOME=%DIRNAME%

FOR %%i IN ("%APP_HOME%") DO SET APP_HOME=%%~fi

SET DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"
SET WRAPPER_JAR=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
SET JAVA_EXE=

IF NOT DEFINED GRADLE_USER_HOME (
  SET GRADLE_USER_HOME=%APP_HOME%\.gradle-user-home
)

IF NOT EXIST "%WRAPPER_JAR%" (
  ECHO ERROR: Could not find Gradle wrapper JAR at "%WRAPPER_JAR%".
  EXIT /B 1
)

IF DEFINED JAVA_HOME (
  IF EXIST "%JAVA_HOME%\bin\java.exe" (
    SET JAVA_EXE=%JAVA_HOME%\bin\java.exe
  )
)

IF NOT DEFINED JAVA_EXE (
  IF EXIST "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    SET JAVA_EXE=C:\Program Files\Android\Android Studio\jbr\bin\java.exe
  )
)

IF NOT DEFINED JAVA_EXE (
  IF EXIST "C:\Program Files\Android\Android Studio\jre\bin\java.exe" (
    SET JAVA_EXE=C:\Program Files\Android\Android Studio\jre\bin\java.exe
  )
)

IF NOT DEFINED JAVA_EXE (
  SET JAVA_EXE=java
)

IF /I NOT "%JAVA_EXE%"=="java" IF NOT EXIST "%JAVA_EXE%" (
  ECHO ERROR: Could not find a Java runtime.
  ECHO Checked JAVA_HOME and the Android Studio bundled JBR locations.
  EXIT /B 1
)

IF /I "%JAVA_EXE%"=="java" (
  %JAVA_EXE% %DEFAULT_JVM_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -jar "%WRAPPER_JAR%" %*
) ELSE (
  "%JAVA_EXE%" %DEFAULT_JVM_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -jar "%WRAPPER_JAR%" %*
)
SET EXIT_CODE=%ERRORLEVEL%

ENDLOCAL & EXIT /B %EXIT_CODE%
