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
@FOR /F "usebackq tokens=1* delims==" %%A IN (`powershell -noprofile "& {$scriptDir='%~dp0'; $script='%__MVNW_ARG0_NAME__%'; icm -ScriptBlock ([Scriptblock]::Create((Get-Content -Raw '%~f0'))) -NoNewScope}"`) DO @(
  IF "%%A"=="MVN_CMD" (set __MVNW_CMD__=%%B) ELSE IF "%%B"=="" (echo.%%A) ELSE (echo.%%A=%%B)
)
@SET PSModulePath=%__MVNW_PSMODULEP_SAVE__%
@SET __MVNW_PSMODULEP_SAVE__=
@SET __MVNW_ARG0_NAME__=
@SET MVNW_USERNAME=
@SET MVNW_PASSWORD=
@IF NOT "%__MVNW_CMD__%"=="" (%__MVNW_CMD__% %*)
@echo Cannot run mvnw, run 'mvn' instead
@exit /b 1

: end batch / begin powershell #>

$ErrorActionPreference = "Stop"
if ($env:MVNW_VERBOSE -eq "true") {
  $VerbosePreference = "Continue"
}

$distributionUrl = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip"
$distributionSha256 = "6eedd2cae3626d6ad3a5c9ee324bd265853d64297f07f033430755bd0e0c3a4b"

$MAVEN_HOME = "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.6"

function Download-Maven {
    $zipFile = "$MAVEN_HOME\apache-maven-3.9.6-bin.zip"
    
    if (Test-Path "$MAVEN_HOME\apache-maven-3.9.6\bin\mvn.cmd") {
        return "$MAVEN_HOME\apache-maven-3.9.6"
    }
    
    Write-Verbose "Downloading Maven from $distributionUrl"
    New-Item -ItemType Directory -Force -Path $MAVEN_HOME | Out-Null
    
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -Uri $distributionUrl -OutFile $zipFile
    
    # Verify SHA256
    $hash = (Get-FileHash -Path $zipFile -Algorithm SHA256).Hash.ToLower()
    if ($hash -ne $distributionSha256) {
        Remove-Item $zipFile
        throw "SHA256 verification failed. Expected: $distributionSha256, Got: $hash"
    }
    
    Write-Verbose "Extracting Maven..."
    Expand-Archive -Path $zipFile -DestinationPath $MAVEN_HOME -Force
    Remove-Item $zipFile
    
    return "$MAVEN_HOME\apache-maven-3.9.6"
}

$mavenHome = Download-Maven
$mvnCmd = "$mavenHome\bin\mvn.cmd"

if (Test-Path $mvnCmd) {
    "MVN_CMD=$mvnCmd"
} else {
    throw "Maven command not found at $mvnCmd"
}
