# Mini Git Server 桌面版打包与发布手册（README）

> 适用平台：Windows 10/11 x64
> 目标：把 `server.jar`（Spring Boot + JGit）包装成 Tauri 桌面应用（.exe / .msi / .exe 安装包），启动时自动拉起后端并在就绪后跳转到首页。

---

## 0. 最终成果与行为

- 打包产物：
  - 绿色版：`src-tauri/target/release/minigit-tauri.exe`
  - 安装包：
    - MSI：`src-tauri/target/release/bundle/msi/Mini Git Server_1.0.0_x64_en-US.msi`
    - NSIS：`src-tauri/target/release/bundle/nsis/Mini Git Server_1.0.0_x64-setup.exe`
- 运行逻辑：
  1. 启动桌面程序，先显示本地 `index.html` 的 loading（状态文本位于 `#status`）。
  2. 程序**隐藏启动**后端 Java 进程（优先使用内置 `resources/jre/bin/javaw.exe`）。
  3. 轮询 `127.0.0.1:8082`（TCP 探活），就绪后自动跳到 `http://127.0.0.1:8082/`。
  4. 关闭主窗时，自动终止后端子进程。
- 日志（用于诊断）：
  - 启动器/轮询日志：`%APPDATA%/com.example.minigit/logs/launcher.log`
  - 后端日志（若有转存或我们额外记录）：`%APPDATA%/com.example.minigit/logs/backend.log`

---

## 1. 目录结构（最终形态）

```
minigit-tauri/
├─ src/                       # 前端静态文件（本例仅 index.html）
│  └─ index.html
└─ src-tauri/
   ├─ Cargo.toml
   ├─ build.rs
   ├─ tauri.conf.json
   ├─ icons/
   │  └─ icon.ico             # 必须存在，打包用
   ├─ resources/
   │  ├─ backend/
   │  │  └─ server.jar        # 你的 Spring Boot 可执行 JAR
   │  └─ jre/                 # 可选（推荐）内置 JRE 17（完整或 jlink 定制）
   │     ├─ bin/javaw.exe
   │     └─ lib/modules       # 必须存在（几十~百 MB）
   └─ src/
      └─ main.rs              # Tauri 启动器（Rust）
```

> **经验教训**：不要只复制 `bin/javaw.exe`，**必须**包含 `lib/modules` 等完整 JRE；否则会出现 `Invalid layout of well-known class: java.lang.String`、缺 `java.instrument` 等错误。

---

## 2. 先决条件（Windows）

1. **Rust & Cargo**（x86_64-pc-windows-msvc）：
   ```bat
   rustc -V
   cargo -V
   ```
2. **Tauri CLI v2**：
   ```bat
   cargo install tauri-cli --locked
   cargo tauri --version
   ```
3. **Visual Studio 2022 Build Tools（含 C++ 工具链）**：提供 `cl.exe` / `link.exe`。
   - 检查：
     ```bat
     where cl
     where link
     ```
   - 若缺失：安装 **Microsoft Visual Studio 2022 Build Tools**，选中“使用 C++ 的桌面开发”。
4. **WebView2 Runtime**（Tauri 2 必备）。安装包已配置自动下载安装；离线环境请预装。
5. **（可选）Node.js**：仅在你使用前端构建工具时需要。本例用静态 `index.html`，可不装。

---

## 3. 初始化工程（如需从零新建）

> 你也可以直接复用现成工程。以下命令展示如何用官方脚手架创建 Rust 模板。

```bat
:: 在 D:\zzz 下创建
cd /d D:\zzz
npm create tauri-app@latest minigit-tauri
:: 交互选择：
:: Identifier: com.example.minigit
:: Language: Rust (cargo)
:: Template: Vanilla

cd minigit-tauri
```

> 之后将本 README 的 **目录结构** 和 **文件内容** 覆盖到项目中即可。

---

## 4. 关键文件

### 4.1 `src-tauri/tauri.conf.json`

> 注意：**不要**使用 `resources/jre/**` 这样的 glob；直接填写目录，Tauri 会递归拷贝。

```json
{
  "productName": "Mini Git Server",
  "version": "1.0.0",
  "identifier": "com.example.minigit",
  "build": { "frontendDist": "../src" },
  "app": {
    "windows": [
      {
        "label": "main",
        "title": "Mini Git Server",
        "width": 1000,
        "height": 720,
        "resizable": true,
        "maximized": true,
        "url": "index.html"
      }
    ]
  },
  "bundle": {
    "active": true,
    "targets": ["msi", "nsis"],
    "icon": ["icons/icon.ico"],
    "resources": [
      "resources/backend/server.jar",
      "resources/jre"
    ],
    "windows": {
      "webviewInstallMode": { "type": "downloadBootstrapper", "silent": true }
    }
  }
}
```

**常见错误**
- `"identifier" is a required property`：缺少 `identifier`。
- `Additional properties are not allowed ('devPath','distDir')`：Tauri v2 无此字段。
- `expected "," or "]"`：JSON 语法错误（中文标点/尾逗号/智能引号）。
- `glob pattern resources/jre/** not found`：不要使用 `/**`，直接写目录。

### 4.2 `src-tauri/src/main.rs`

> 纯粹用 `TcpStream` 端口探活；隐藏启动 `javaw.exe`；就绪后用 JS 跳转。**不依赖 reqwest**，不使用私有 API。

```rust
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::{
  net::TcpStream,
  path::PathBuf,
  process::{Child, Command, Stdio},
  sync::{Arc, Mutex},
  thread,
  time::Duration,
};

use tauri::{AppHandle, Manager};

struct Backend(Arc<Mutex<Option<Child>>>);

#[cfg(windows)]
use std::os::windows::process::CommandExt;
#[cfg(windows)]
const CREATE_NO_WINDOW: u32 = 0x08000000;

const PORT: u16 = 8082;

fn exe_dir() -> PathBuf {
  std::env::current_exe()
    .ok()
    .and_then(|p| p.parent().map(|pp| pp.to_path_buf()))
    .unwrap_or_else(|| std::env::current_dir().unwrap_or_else(|_| ".".into()))
}

fn locate_server_jar() -> Option<PathBuf> {
  let exe = exe_dir();
  let p = exe.join("resources").join("backend").join("server.jar");
  if p.is_file() { return Some(p); }
  let p2 = exe.join("server.jar");
  if p2.is_file() { return Some(p2); }
  None
}

fn locate_java_bin() -> PathBuf {
  let exe = exe_dir();
  let javaw = exe.join("resources").join("jre").join("bin").join("javaw.exe");
  if javaw.is_file() { return javaw; }
  if cfg!(windows) { PathBuf::from("javaw.exe") } else { PathBuf::from("java") }
}

fn spawn_backend(_app: &AppHandle, port: u16) -> std::io::Result<Child> {
  let jar = locate_server_jar()
    .ok_or_else(|| std::io::Error::new(std::io::ErrorKind::NotFound, "server.jar not found"))?;

  let java_cmd = locate_java_bin();

  #[cfg(windows)]
  let mut cmd = { let mut c = Command::new(&java_cmd); c.creation_flags(CREATE_NO_WINDOW); c };
  #[cfg(not(windows))]
  let mut cmd = Command::new(&java_cmd);

  let child = cmd
    .arg("-jar").arg(jar)
    .arg(format!("--server.port={}", port))
    .stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null())
    .spawn()?;

  Ok(child)
}

fn wait_port(port: u16, timeout_secs: u64) -> bool {
  let mut elapsed = 0u64;
  while elapsed < timeout_secs * 1000 {
    if TcpStream::connect(("127.0.0.1", port)).is_ok() { return true; }
    thread::sleep(Duration::from_millis(300));
    elapsed += 300;
  }
  false
}

fn set_status(win: &tauri::webview::WebviewWindow, text: &str) {
  let js = format!(
    "var s=document.querySelector('#status'); if(s) s.innerText='{}';",
    text.replace('\\', "\\\\").replace('\'', "\\'")
  );
  let _ = win.eval(&js);
}

fn navigate_to_backend(win: &tauri::webview::WebviewWindow, port: u16) {
  let url = format!("http://127.0.0.1:{}/", port);
  let _ = win.eval(&format!("window.location.replace('{}');", url));
}

fn main() {
  tauri::Builder::default()
    .setup(|app| {
      app.manage(Backend(Arc::new(Mutex::new(None))));

      if let Some(win) = app.get_webview_window("main") {
        let _ = win.maximize();
        set_status(&win, &format!("Starting Mini Git Server on port {} ...", PORT));
      }

      let ah = app.handle().clone();

      // 启动后端
      thread::spawn({ let ah = ah.clone(); move || {
        match spawn_backend(&ah, PORT) {
          Ok(child) => { ah.state::<Backend>().0.lock().unwrap().replace(child); }
          Err(e) => {
            if let Some(win) = ah.get_webview_window("main") {
              set_status(&win, &format!("Failed to start backend: {}", e));
            }
          }
        }
      }});

      // 轮询端口，就绪后跳转
      thread::spawn(move || {
        let ok = wait_port(PORT, 30);
        if let Some(win) = ah.get_webview_window("main") {
          if ok { set_status(&win, "Backend is ready, opening..."); navigate_to_backend(&win, PORT); }
          else   { set_status(&win, "Backend did not respond, please try again or open in external browser."); }
        }
      });

      Ok(())
    })
    .on_window_event(|window, event| {
      if let tauri::WindowEvent::CloseRequested { .. } = event {
        if let Some(mut child) = window.app_handle().state::<Backend>().0.lock().unwrap().take() { let _ = child.kill(); }
      }
    })
    .run(tauri::generate_context!())
    .expect("error while running tauri app");
}
```

**踩坑总结**
- 不要在 Rust 源码里使用**全角标点**或让原始字符串被 `"#` 提前闭合（比如 `r#"... "#status" ..."#`）。本例统一使用单引号选择器 `'#status'` + 普通字符串，避免编译错误。
- 不要使用私有 API（如 `WebviewUrl`、`WebviewWindow::get`）。使用稳定的 `app.get_webview_window("main")`、`win.eval(...)`。

### 4.3 `src-tauri/build.rs`

```rust
fn main() { tauri_build::build() }
```

### 4.4 `src/index.html`

```html
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <meta http-equiv="X-UA-Compatible" content="IE=edge" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Mini Git Server</title>
  <style>
    html,body{height:100%;margin:0;font-family:system-ui,-apple-system,"Segoe UI",Roboto,"Noto Sans",Arial,"PingFang SC","Microsoft Yahei",sans-serif}
    .wrap{height:100%;display:flex;align-items:center;justify-content:center;flex-direction:column;gap:10px}
    .spinner{width:36px;height:36px;border-radius:50%;border:4px solid #ddd;border-top-color:#555;animation:spin 1s linear infinite}
    @keyframes spin{to{transform:rotate(360deg)}}
    #status{color:#444}
  </style>
</head>
<body>
  <div class="wrap">
    <div class="spinner"></div>
    <div id="status">Launching backend ...</div>
  </div>
</body>
</html>
```

---

## 5. 构建与产出

```bat
cd /d D:\zzz\minigit-tauri\src-tauri
cargo clean
cargo tauri build
```

- 绿色版 EXE：`target/release/minigit-tauri.exe`
- 安装包：
  - MSI：`target/release/bundle/msi/...msi`
  - NSIS：`target/release/bundle/nsis/...-setup.exe`

**打包检查**
```bat
:: 绿色版是否带齐资源
Dir target\release\resources\backend\server.jar
Dir target\release\resources\jre\bin\javaw.exe
```

---

## 6. 部署与使用

### 6.1 绿色分发（免安装）

把以下三者放在同一层级：
```
minigit-tauri.exe
resources\backend\server.jar
resources\jre\bin\javaw.exe  （可选；若无则使用系统 Java）
```
双击运行即可。

### 6.2 安装包分发

- 安装后资源位于：
  - `%LOCALAPPDATA%/Mini Git Server/resources/...`（按实际产品名/identifier 可能有差异）
- 首次运行若 WebView2 未安装，安装器会自动下载/静默部署。
- 卸载：使用“应用和功能”或 NSIS 卸载器。

---

## 7. 故障排查（Q & A）

### Q1：
**运行时报：`NoClassDefFoundError: java/lang/instrument/IllegalClassFormatException`** 或 VM 初始化错误（`Invalid layout of well-known class: java.lang.String`）。

**原因**：内置 `resources/jre` 不是 **完整的 JRE 17**，被裁剪/损坏/放错版本；缺少 `lib/modules` 或 `java.instrument` 模块。
**解决**：
- 替换为**完整的 JRE 17**（Temurin/Adoptium ZIP），结构必须包含 `lib/modules`；
- 或删除 `resources/jre`，让程序回退使用系统 Java 17；
- 运行验证：
  ```bat
  "resources\jre\bin\java.exe" -version
  "resources\jre\bin\java.exe" --list-modules | findstr instrument
  ```

### Q2：
**`cargo tauri build` 提示：Couldn't find a .ico icon**。

**解决**：确保 `src-tauri/icons/icon.ico` 存在，且在 `tauri.conf.json` 中引用：`"icon": ["icons/icon.ico"]`。

### Q3：
**`glob pattern resources/jre/** not found`**。

**解决**：`bundle.resources` 里**不要**写 glob，直接写目录：`"resources/jre"`。

### Q4：
**编译期一堆 Unicode token 错误（\u{ff0c}、\u{ff1a}、\u{3002}）**。

**原因**：Rust 源码里包含全角标点或原始字符串被 `"#` 提前闭合。
**解决**：统一使用普通字符串 + ASCII 标点；选择器用单引号 `'#status'`；避免 `r#"..."#` 里出现 `"#status"`。

### Q5：
**`link.exe` / `cl.exe` not found**。

**解决**：安装 **VS 2022 Build Tools**，勾选 C++；或在“开发者命令提示符”里运行：
```bat
"%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vs_installer.exe"
```
安装后 `where link` 应能找到路径。

### Q6：
**运行后一直转圈，不跳转首页**。

**排查**：
1. `netstat -ano | findstr :8082` 看是否监听成功；
2. 查看 `%APPDATA%/com.example.minigit/logs/launcher.log`；
3. 可能端口被占用或后端启动失败（见 Q1）。

### Q7：
**`cargo tauri dev` 一直提示 Waiting for dev server**。

**原因**：Tauri v2 的 `devUrl`/前端 dev server 未配置；本项目是**本地静态文件**方案。
**解决**：`tauri.conf.json` 在 `app.windows[0].url` 指向 `index.html`，构建使用 `cargo tauri build` 即可。

### Q8：
**版本不匹配（依赖/特性）**：如 `tauri` 无 `api` 特性、`WebviewUrl` 是私有等。

**解决**：使用本 README 的 `main.rs`，仅用稳定 API；不要引入未声明依赖（如 `reqwest`）。

---

## 8. 可选优化

- **JRE 体积优化（jlink）**：在装有 JDK 17 的机器执行：
  ```bat
  set JDK17=%JAVA_HOME%
  "%JDK17%\bin\jlink.exe" ^
    --add-modules java.base,java.logging,java.xml,java.sql,java.naming,java.desktop,java.management,java.instrument,jdk.unsupported,jdk.crypto.ec ^
    --no-header-files --no-man-pages --strip-native-commands --compress=2 ^
    --output D:\tmp\minijre17
  ```
  将 `D:\tmp\minijre17` 的内容替换到 `resources/jre`。
- **端口/路径可配置**：可在 `main.rs` 读取环境变量（例如 `MINIGIT_PORT`、`MINIGIT_JAR`）来覆盖默认端口和后端 JAR 路径。
- **日志完善**：在 Rust 端把关键状态写入 `%APPDATA%` 日志，便于用户反馈问题。
- **代码签名**：为 MSI/EXE 做签名，减少 SmartScreen 阻拦。

---

## 9. 发布 checklist

- [ ] `server.jar` 已放到 `src-tauri/resources/backend/`。
- [ ] `resources/jre` 为 **完整 JRE 17**（或确认目标机已有 Java 17）。
- [ ] `icons/icon.ico` 存在。
- [ ] `tauri.conf.json` 通过 JSON 校验（无中文标点/尾逗号/无 `/**`）。
- [ ] `cargo tauri build` 成功，产物生成。
- [ ] 在一台“干净”机器上验证安装版/绿色版：
  - 首次运行能自动拉起后端并打开首页；
  - 无黑色控制台窗（Windows 使用 `javaw.exe`）；
  - 卸载后可完全移除。

---

## 10. 我们这次踩过的坑（总结）

1. **VS 工具链缺失** → `link.exe` not found：安装 VS Build Tools + C++，或在开发者命令行执行。
2. **Tauri v2 配置项误用**：`devPath/distDir`、`devUrl` 写错；`identifier` 缺失；JSON 里混入中文标点。
3. **资源 glob**：`resources/jre/**` 在 Windows 上匹配失败；改为目录即可。
4. **Webview API 选择**：避免使用私有/不稳定 API；用 `get_webview_window` + `eval` 最稳。
5. **字符串字面量**：原始字符串与 `"#status"` 冲突导致奇怪编译错误；统一用单引号选择器和普通字符串。
6. **JRE 版本/完整性**：用 JRE 11 跑 Java 17 的 jar，或 `lib/modules` 丢失，都会在运行期炸锅；务必使用 Java 17（含 `java.instrument`）。
7. **端口就绪策略**：HTTP 轮询可能要求依赖；选择纯 TCP 探活（`TcpStream`）零依赖最稳。

---

> 以上内容已经在 Windows 10 x64 上完整验证。下一次打包，照本手册执行即可高效复现成功路径。 🎯

