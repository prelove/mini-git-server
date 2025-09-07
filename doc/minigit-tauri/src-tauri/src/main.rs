// src-tauri/src/main.rs
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

// 保存后端子进程，便于退出时清理
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
  // 1) EXE 同目录 resources\backend\server.jar
  let p = exe.join("resources").join("backend").join("server.jar");
  if p.is_file() {
    return Some(p);
  }
  // 2) EXE 同目录下的 server.jar（便携场景）
  let p2 = exe.join("server.jar");
  if p2.is_file() {
    return Some(p2);
  }
  None
}

fn locate_java_bin() -> PathBuf {
  let exe = exe_dir();
  // 优先内置 JRE（隐藏黑窗使用 javaw.exe）
  let javaw = exe.join("resources").join("jre").join("bin").join("javaw.exe");
  if javaw.is_file() {
    return javaw;
  }
  // 退回 PATH
  if cfg!(windows) {
    PathBuf::from("javaw.exe")
  } else {
    PathBuf::from("java")
  }
}

fn spawn_backend(_app: &AppHandle, port: u16) -> std::io::Result<Child> {
  let jar = locate_server_jar()
    .ok_or_else(|| std::io::Error::new(std::io::ErrorKind::NotFound, "server.jar not found"))?;

  let java_cmd = locate_java_bin();

  #[cfg(windows)]
  let mut cmd = {
    let mut c = Command::new(&java_cmd);
    c.creation_flags(CREATE_NO_WINDOW); // 隐藏黑窗
    c
  };

  #[cfg(not(windows))]
  let mut cmd = Command::new(&java_cmd);

  let child = cmd
    .arg("-jar")
    .arg(jar)
    .arg(format!("--server.port={}", port))
    .stdin(Stdio::null())
    .stdout(Stdio::null())
    .stderr(Stdio::null())
    .spawn()?;

  Ok(child)
}

fn wait_port(port: u16, timeout_secs: u64) -> bool {
  let mut elapsed = 0u64;
  while elapsed < timeout_secs * 1000 {
    if TcpStream::connect(("127.0.0.1", port)).is_ok() {
      return true;
    }
    thread::sleep(Duration::from_millis(300));
    elapsed += 300;
  }
  false
}

fn set_status(win: &tauri::webview::WebviewWindow, text: &str) {
  // 使用单引号，避免与 Rust 字符串冲突；全部 ASCII 标点，防止编译器把全角标点当非法 token
  let js = format!(
    "var s=document.querySelector('#status'); if(s) s.innerText='{}';",
    text.replace('\\', "\\\\").replace('\'', "\\'")
  );
  let _ = win.eval(&js);
}

fn navigate_to_backend(win: &tauri::webview::WebviewWindow, port: u16) {
  let url = format!("http://127.0.0.1:{}/", port);
  // 直接用 JS 跳转，避免使用私有 WebviewUrl 枚举
  let _ = win.eval(&format!("window.location.replace('{}');", url));
}

fn main() {
  tauri::Builder::default()
    .setup(|app| {
      // 管理后端进程
      app.manage(Backend(Arc::new(Mutex::new(None))));

      // 取主窗口并最大化 + 初始提示
      if let Some(win) = app.get_webview_window("main") {
        let _ = win.maximize();
        set_status(&win, &format!("Starting Mini Git Server on port {} ...", PORT));
      }

      let ah = app.handle().clone();

      // 启动后端
      thread::spawn({
        let ah = ah.clone();
        move || {
          match spawn_backend(&ah, PORT) {
            Ok(child) => {
              ah.state::<Backend>().0.lock().unwrap().replace(child);
            }
            Err(e) => {
              if let Some(win) = ah.get_webview_window("main") {
                set_status(&win, &format!("Failed to start backend: {}", e));
              }
            }
          }
        }
      });

      // 轮询端口，就绪后跳转
      thread::spawn(move || {
        let ok = wait_port(PORT, 30);
        if let Some(win) = ah.get_webview_window("main") {
          if ok {
            set_status(&win, "Backend is ready, opening...");
            navigate_to_backend(&win, PORT);
          } else {
            set_status(&win, "Backend did not respond, please try again or open in external browser.");
          }
        }
      });

      Ok(())
    })
    .on_window_event(|window, event| {
      if let tauri::WindowEvent::CloseRequested { .. } = event {
        if let Some(mut child) = window.app_handle().state::<Backend>().0.lock().unwrap().take() {
          let _ = child.kill();
        }
      }
    })
    .run(tauri::generate_context!())
    .expect("error while running tauri app");
}
