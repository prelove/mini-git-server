@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem ====== 配置区：找到你的 client JAR ======
rem 优先使用环境变量（可在系统里永久设置）
if defined MGIT_JAR (
  set "JAR=%MGIT_JAR%"
) else (
  rem 方案1：git.bat 与 JAR 放一起
  set "JAR=%~dp0mini-git-server-1.0.0-client.jar"
  if not exist "%JAR%" (
    rem 方案2：git.bat 上级的 target 目录（常见于项目根下的 scripts\git.bat）
    set "JAR=%~dp0..\target\mini-git-server-1.0.0-client.jar"
  )
)
if not exist "%JAR%" (
  echo [git.bat] Cannot find client jar. Set MGIT_JAR env or edit this script.
  echo Example: set MGIT_JAR=E:\idea_projects\mini-git-server\target\mini-git-server-1.0.0-client.jar
  exit /b 1
)

rem ====== 解析 -C 目录（可多次出现，最后一次生效）======
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

rem ====== 没有子命令：显示帮助 ======
if "%~1"=="" (
  echo Mini Git (mgit) wrapper - using %JAR%
  echo.
  echo Supported: init, clone, status, add, commit, log, branch, checkout, remote -v, push, pull
  echo Tips: use -C ^<path^> before the command to operate another repo directory.
  exit /b 0
)

set "CMD=%~1"
shift

rem ====== 常见命令映射 ======
if /I "%CMD%"=="--version" (
  echo git version (mgit wrapper)
  exit /b 0
)

if /I "%CMD%"=="help" (
  java -jar "%JAR%" help
  exit /b %ERRORLEVEL%
)

if /I "%CMD%"=="init" (
  rem git -C X init  -> mgit init X（若未给目标目录，则用 -C 指定或当前目录）
  if "%~1"=="" ( set "TARGET=%REPO%" ) else ( set "TARGET=%~1" )
  java -jar "%JAR%" init "%TARGET%"
  exit /b %ERRORLEVEL%
)

if /I "%CMD%"=="clone" (
  rem 原样转发：git clone <url> <dir> [--user U --pass P]
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
  rem 透传 commit 选项（-m 等），自动加上 repo
  java -jar "%JAR%" commit "%REPO%" %*
  exit /b %ERRORLEVEL%
)

if /I "%CMD%"=="log" (
  rem 支持 --max N 透传
  java -jar "%JAR%" log "%REPO%" %*
  exit /b %ERRORLEVEL%
)

if /I "%CMD%"=="branch" (
  rem 列表：git branch -> mgit branch <repo>
  rem 创建：git branch -c <name> -> mgit branch -c <repo> <name>
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
  rem 仅支持 -v
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

rem ====== 未覆盖的子命令 ======
echo [git.bat] Unsupported command: %CMD%
echo Supported: init, clone, status, add, commit, log, branch, checkout, remote -v, push, pull
exit /b 3
