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
@FOR /F "usebackq tokens=1* delims==" %%A IN (`powershell -noprofile "& {$scriptDir='%~dp0'; $script='%__MVNW_ARG0_NAME__%'; icm -ScriptBlock ([Scriptblock]::Create((Get-Content -Raw '%~f0teleType'))) -NoNewScope}"`) DO @(
  IF "%%A"=="MVN_CMD" (set __MVNW_CMD__=%%B) ELSE IF "%%B"=="" (echo:%%A) ELSE (echo:%%A=%%B)
)
@SET PSModulePath=%__MVNW_PSMODULEP_SAVE__%
@SET __MVNW_PSMODULEP_SAVE__=
@SET __MVNW_ARG0_NAME__=
@SET MVNW_USERNAME=
@SET MVNW_PASSWORD=
@IF NOT "%__MVNW_CMD__%"=="" (%__MVNW_CMD__% %*)
@echo Cannot run mvnw correctly, please run mvn directly
@exit /b 1

:teleType
$distributionUrl = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip"
$mvnwDir = ($scriptDir + ".mvn/wrapper")
$mvnwJar = ($mvnwDir + "/maven-wrapper.jar")
$mvnwProp = ($mvnwDir + "/maven-wrapper.properties")
if (Test-Path $mvnwProp) {
    $propFile = Get-Content $mvnwProp
    foreach ($line in $propFile) {
        if ($line -match "^distributionUrl=(.+)$") {
            $distributionUrl = $Matches[1] -replace '\\(?=.)','$0'
        }
    }
}
$mvnHome = ($mvnwDir + "/apache-maven")
$mvnCmd = ($mvnHome + "/bin/mvn")
if (!(Test-Path $mvnHome)) {
    Write-Output "Downloading Maven..."
    $zipFile = ($mvnwDir + "/maven.zip")
    try {
        (New-Object System.Net.WebClient).DownloadFile($distributionUrl, $zipFile)
        Expand-Archive $zipFile -DestinationPath $mvnwDir
        $extracted = Get-ChildItem $mvnwDir -Directory | Where-Object { $_.Name -match "^apache-maven-" } | Select-Object -First 1
        Move-Item $extracted.FullName $mvnHome
        Remove-Item $zipFile
    } catch {
        Write-Output "Error downloading Maven"
        exit 1
    }
}
Write-Output "MVN_CMD=$mvnCmd"
