@echo off
set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
java -jar "%~dp0/../kobalt/wrapper/kobalt-wrapper.jar" %*
