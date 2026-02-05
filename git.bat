@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem ====== Config: locate the client JAR ======
rem Prefer the environment variable (can be set permanently).
if defined MGIT_JAR (
  set "JAR=%MGIT_JAR%"
) else (
  rem Option 1: git.bat next to the JAR.
  set "JAR=%~dp0mini-git-server-1.0.0-client.jar"
  if not exist "%JAR%" (
    rem Option 2: target directory above git.bat (common in scripts\git.bat under repo root).
    set "JAR=%~dp0..\target\mini-git-server-1.0.0-client.jar"
  )
)
if not exist "%JAR%" (
  echo [git.bat] Cannot find client jar. Set MGIT_JAR env or edit this script.
  echo Example: set MGIT_JAR=E:\idea_projects\mini-git-server\target\mini-git-server-1.0.0-client.jar
  exit /b 1
)

rem ====== Parse -C directory (can appear multiple times, last wins) ======
set "REPO=%CD%"
:parseC
if /I "%~1"=="-C" (
  if "%~2"=="" (
    echo usage: git -C ^<path^> <command> ...
    exit /b 2
  )
  set "REPO=%~f2"
  shift
  shift
  goto parseC
)

rem ====== No subcommand: show help ======
if "%~1"=="" (
  echo Mini Git (mgit) wrapper - using %JAR%
  echo.
  echo Supported: init, clone, status, add, commit, log, branch, checkout, remote -v, push, pull
  echo Tips: use -C ^<path^> before the command to operate another repo directory.
  exit /b 0
)

set "CMD=%~1"
shift

rem ====== Common command mapping ======
if /I "%CMD%"=="--version" (
  echo git version (mgit wrapper)
  exit /b 0
)

if /I "%CMD%"=="help" (
  java -jar "%JAR%" help
  exit /b %ERRORLEVEL%
)

if /I "%CMD%"=="init" (
  rem git -C X init  -> mgit init X (use -C or current directory when target is omitted).
  if "%~1"=="" ( set "TARGET=%REPO%" ) else ( set "TARGET=%~1" )
  java -jar "%JAR%" init "%TARGET%"
  exit /b %ERRORLEVEL%
)

if /I "%CMD%"=="clone" (
  rem Pass-through: git clone <url> <dir> [--user U --pass P]
  java -jar "%JAR%" clone %*
  exit /b %ERRORLEVEL%
)

if /I "%CMD%"=="status" (
  java -jar "%JAR%" status "%REPO%"
  exit /b %ERRORLEVEL%
)

if /I "%CMD%"=="add" (
  rem git add <paths...>  -> mgit add <repo> <paths...>
  if "%~1"=="" (
    echo usage: git add ^<paths...^>
    exit /b 2
  )
  java -jar "%JAR%" add "%REPO%" %*
  exit /b %ERRORLEVEL%
)

if /I "%CMD%"=="commit" (
  rem Pass through commit options (-m, etc) and add repo automatically.
  java -jar "%JAR%" commit "%REPO%" %*
  exit /b %ERRORLEVEL%
)

if /I "%CMD%"=="log" (
  rem Pass through --max N.
  java -jar "%JAR%" log "%REPO%" %*
  exit /b %ERRORLEVEL%
)

if /I "%CMD%"=="branch" (
  rem List: git branch -> mgit branch <repo>
  rem Create: git branch -c <name> -> mgit branch -c <repo> <name>
  if /I "%~1"=="-c" (
    if "%~2"=="" (
      echo usage: git branch -c ^<name^>
      exit /b 2
    )
    java -jar "%JAR%" branch -c "%REPO%" "%~2"
  ) else (
    java -jar "%JAR%" branch "%REPO%"
  )
  exit /b %ERRORLEVEL%
)

if /I "%CMD%"=="checkout" (
  if "%~1"=="" (
    echo usage: git checkout ^<branchOrCommit^>
    exit /b 2
  )
  java -jar "%JAR%" checkout "%REPO%" %*
  exit /b %ERRORLEVEL%
)

if /I "%CMD%"=="remote" (
  rem Only -v is supported.
  if /I "%~1"=="-v" (
    java -jar "%JAR%" remote -v "%REPO%"
    exit /b %ERRORLEVEL%
  )
)

if /I "%CMD%"=="push" (
  rem git push <remote> <ref> [--user U --pass P]
  if "%~2"=="" (
    echo usage: git push ^<remote^> ^<ref^> [--user U --pass P]
    exit /b 2
  )
  java -jar "%JAR%" push "%REPO%" %*
  exit /b %ERRORLEVEL%
)

if /I "%CMD%"=="pull" (
  rem git pull <remote> <ref> [--user U --pass P]
  if "%~2"=="" (
    echo usage: git pull ^<remote^> ^<ref^> [--user U --pass P]
    exit /b 2
  )
  java -jar "%JAR%" pull "%REPO%" %*
  exit /b %ERRORLEVEL%
)

rem ====== Unsupported subcommands ======
echo [git.bat] Unsupported command: %CMD%
echo Supported: init, clone, status, add, commit, log, branch, checkout, remote -v, push, pull
exit /b 3
