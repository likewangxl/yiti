@echo off
chcp 65001 >nul
setlocal EnableExtensions

cd /d "%~dp0"
echo [红色引擎任务原型] 正在检查运行环境...

where node >nul 2>&1
if errorlevel 1 (
  echo 未检测到 Node.js。请先安装 Node.js 18 或更高版本后重试。
  pause
  exit /b 1
)

where npm >nul 2>&1
if errorlevel 1 (
  echo 未检测到 npm。请确认 Node.js 安装已加入 PATH 后重试。
  pause
  exit /b 1
)

for /f "tokens=*" %%V in ('node -p "process.versions.node"') do set "NODE_VERSION=%%V"
for /f "tokens=1 delims=." %%V in ("%NODE_VERSION%") do set "NODE_MAJOR=%%V"
if %NODE_MAJOR% LSS 18 (
  echo 当前 Node.js 版本为 %NODE_VERSION%，需要 Node.js 18 或更高版本。
  pause
  exit /b 1
)

set "NEED_INSTALL=0"
if not exist "node_modules\react\package.json" set "NEED_INSTALL=1"
if not exist "node_modules\@tabler\icons-react\package.json" set "NEED_INSTALL=1"
if not exist "node_modules\.prototype-windows-ready" set "NEED_INSTALL=1"

if "%NEED_INSTALL%"=="1" (
  echo 未发现依赖，正在执行 npm install（首次安装可能需要网络或本地 npm 缓存）...
  call npm install --prefer-offline --no-audit --no-fund
  if errorlevel 1 (
    echo 依赖安装失败。请检查网络或 npm 缓存后重新运行本脚本；窗口将保留供查看错误信息。
    pause
    exit /b 1
  )
  type nul > "node_modules\.prototype-windows-ready"
)

echo 正在启动 Vite 原型，地址为 http://127.0.0.1:4173
start "红色引擎任务原型" /D "%~dp0" cmd /c "npm run dev -- --host 127.0.0.1 --port 4173 --strictPort"
timeout /t 2 /nobreak >nul
start "" "http://127.0.0.1:4173"
echo 已打开默认浏览器。请保留 Vite 窗口以继续运行原型。
endlocal
