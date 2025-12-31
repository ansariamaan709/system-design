@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup batch script, version 3.2.0
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET __MVNW_CMD__=
@SET __MVNW_ERROR__=
@SET __MVNW_PSMODULEP_SAVE__=%PSModulePath%
@SET PSModulePath=
@FOR /F "usebackq tokens=1* delims==" %%A IN (`powershell -noprofile "& {$scriptDir='%~dp0'; $env:__MVNW_SCRIPT_DIR__=$scriptDir; & { if (-not (Test-Path $scriptDir'.mvn/wrapper/maven-wrapper.jar')) { $null = New-Item -Path $scriptDir'.mvn/wrapper' -ItemType Directory -Force; [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar' -OutFile $scriptDir'.mvn/wrapper/maven-wrapper.jar' } }; exit $LASTEXITCODE}" ^| MORE`) DO @(
  IF "%%A"=="MVNW_CMD" (SET __MVNW_CMD__=%%B) ELSE IF "%%B"=="" (echo.%%A) ELSE (echo.%%A=%%B)
)
@SET PSModulePath=%__MVNW_PSMODULEP_SAVE__%
@SET __MVNW_PSMODULEP_SAVE__=
@SET __MVNW_ARG0_NAME__=
@SET MVNW_USERNAME=
@SET MVNW_PASSWORD=

@IF NOT "%__MVNW_CMD__%"=="" (%__MVNW_CMD__% %*)
@echo Cannot run maven-wrapper, download manually from: https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar
@IF "%__MVNW_ERROR__%"=="" (@SET __MVNW_ERROR__=1 & @CALL "%~f0" %*)

@ECHO OFF
@SETLOCAL

@SET JAVA_EXE=java.exe
@SET WRAPPER_JAR="%~dp0\.mvn\wrapper\maven-wrapper.jar"
@SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

@IF EXIST "%~dp0\.mvn\wrapper\maven-wrapper.properties" (
    @FOR /F "usebackq tokens=1,2 delims==" %%A IN ("%~dp0\.mvn\wrapper\maven-wrapper.properties") DO @(
        @IF "%%A"=="distributionUrl" @SET MAVEN_DIST=%%B
        @IF "%%A"=="wrapperUrl" @SET WRAPPER_URL=%%B
    )
)

@IF NOT EXIST %WRAPPER_JAR% (
    @ECHO Downloading Maven Wrapper...
    @powershell -Command "(New-Object Net.WebClient).DownloadFile('%WRAPPER_URL%', '%WRAPPER_JAR%')"
)

%JAVA_EXE% %MAVEN_OPTS% -cp %WRAPPER_JAR% %WRAPPER_LAUNCHER% %*
