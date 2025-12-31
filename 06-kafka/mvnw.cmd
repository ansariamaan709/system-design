@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.2.0
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET __MVNW_CMD__=
@SET __MVNW_ERROR__=
@SET __MVNW_PSMODULEP_SAVE__=%PSModulePath%
@SET PSModulePath=
@FOR /F "usebackq tokens=1* delims==" %%A IN (`powershell -noprofile "& {$scriptDir='%~dp0'; $env:__MVNW_SCRIPT__=(Resolve-Path $scriptDir).Path; exit 0; }"`) DO @(
  IF "%%A"=="__MVNW_SCRIPT__" SET "__MVNW_SCRIPT__=%%B"
)
@SET PSModulePath=%__MVNW_PSMODULEP_SAVE__%
@SET __MVNW_PSMODULEP_SAVE__=

@IF NOT EXIST "%__MVNW_SCRIPT__%.mvn\wrapper\maven-wrapper.jar" (
  echo Downloading Maven Wrapper...
  powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar' -OutFile '%__MVNW_SCRIPT__%.mvn\wrapper\maven-wrapper.jar'}"
)

@SET WRAPPER_JAR="%__MVNW_SCRIPT__%.mvn\wrapper\maven-wrapper.jar"
@SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

@SET MAVEN_PROJECTBASEDIR=%__MVNW_SCRIPT__%
@SET MVNW_VERBOSE=false

@FOR /F "usebackq tokens=1,2 delims==" %%A IN ("%__MVNW_SCRIPT__%.mvn\wrapper\maven-wrapper.properties") DO (
    @IF "%%A"=="wrapperUrl" SET WRAPPER_URL=%%B
    @IF "%%A"=="distributionUrl" SET MVNW_REPOURL=%%B
)

@IF NOT EXIST "%__MVNW_SCRIPT__%.mvn\wrapper\maven-wrapper.properties" (
  echo Creating maven-wrapper.properties...
  mkdir "%__MVNW_SCRIPT__%.mvn\wrapper" 2>nul
  echo distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip > "%__MVNW_SCRIPT__%.mvn\wrapper\maven-wrapper.properties"
  echo wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar >> "%__MVNW_SCRIPT__%.mvn\wrapper\maven-wrapper.properties"
)

@SET JAVA_EXE=java.exe
@IF NOT "%JAVA_HOME%"=="" SET JAVA_EXE="%JAVA_HOME%\bin\java.exe"

%JAVA_EXE% ^
  %MAVEN_OPTS% ^
  -classpath %WRAPPER_JAR% ^
  -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" ^
  %WRAPPER_LAUNCHER% %*

@IF %ERRORLEVEL% NEQ 0 GOTO error
@GOTO end

:error
@SET ERROR_CODE=1

:end
@EXIT /B %ERROR_CODE%
