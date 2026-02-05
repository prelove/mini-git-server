# Mini Git Server Desktop Packaging Guide (README)

> Platforms: Windows 10/11 x64
> Goal: Wrap `server.jar` (Spring Boot + JGit) into a Tauri desktop application (.exe / .msi). The launcher starts the backend and opens the UI when ready.

## 0. Target behavior

- Build outputs:
  - Portable: `src-tauri/target/release/minigit-tauri.exe`
  - Installer: `.msi` or `.exe` installer
- Runtime flow:
  1. Launch the desktop app and show the local `index.html` loading page (status text in `#status`).
  2. Start the Java backend in **hidden mode** (prefer bundled `resources/jre/bin/javaw.exe`).
  3. Poll `127.0.0.1:8082` (TCP probe). When ready, redirect to `http://127.0.0.1:8082/`.
  4. When the main window closes, terminate the backend process.
- Logs (for diagnostics):
  - Launcher/polling log: `%APPDATA%/com.example.minigit/logs/launcher.log`
  - Backend log (if captured): `%APPDATA%/com.example.minigit/logs/backend.log`

## 1. Directory structure (final form)

```
project-root/
├─ src/                       # frontend static files (only index.html in this example)
├─ src-tauri/
│  ├─ icons/                  # must exist for bundling
│  │  └─ icon.ico
│  ├─ resources/
│  │  ├─ backend/
│  │  │  └─ server.jar        # Spring Boot executable JAR
│  │  └─ jre/                 # optional (recommended) bundled JRE 17
│  │     └─ lib/modules       # must exist (tens to hundreds of MB)
│  └─ src/
│     └─ main.rs              # Tauri launcher (Rust)
```

> Lesson learned: do not copy only `bin/javaw.exe`. You must include the full JRE (especially `lib/modules`), otherwise you will see errors like `Invalid layout of well-known class: java.lang.String` or missing `java.instrument`.

## 2. Prerequisites (Windows)

1. **Rust toolchain** (stable): https://rustup.rs/
2. **Tauri CLI v2**: `cargo install tauri-cli --version ^2`
3. **Visual Studio 2022 Build Tools (C++ toolchain)**: provides `cl.exe` / `link.exe`.
4. **WebView2 Runtime** (required by Tauri 2). The installer can auto-download; preinstall for offline use.
5. **Node.js** (optional): only needed for frontend build tooling. This guide uses static `index.html`.

## 3. Initialize the project (if starting from scratch)

You can reuse the existing project. Otherwise, use the official scaffold to create a Rust template, then overwrite the structure and files described above.

## 4. Key files

> Avoid `resources/jre/**` globs; use a directory path so Tauri copies recursively.

### Common errors

- `"identifier" is a required property`: missing `identifier` in `tauri.conf.json`.
- `Additional properties are not allowed ('devPath','distDir')`: Tauri v2 does not accept these fields.
- `expected "," or "]"`: JSON syntax error (often caused by trailing commas or invalid characters).
- `glob pattern resources/jre/** not found`: do not use `/**` in Windows paths; point to the directory.

> The launcher uses a plain TCP probe, starts `javaw.exe` in hidden mode, and redirects using JS. It avoids extra dependencies and private APIs.

## 5. Build & output

```bash
cargo tauri build
```

Outputs:
- Portable EXE: `target/release/minigit-tauri.exe`
- Installer: in `target/release/bundle/`

## 6. Distribution

### 6.1 Portable distribution

Place these together in the same directory:

```
minigit-tauri.exe
resources\backend\server.jar
resources\jre\bin\javaw.exe  (optional; falls back to system Java if missing)
```

### 6.2 Installer distribution

- Installed resources typically live under `%LOCALAPPDATA%/Mini Git Server/resources/...` (depends on product name/identifier).
- First launch may install WebView2 automatically.
- Uninstall via “Apps & features” or the NSIS uninstaller.

## 7. Troubleshooting (Q&A)

**Q1: `NoClassDefFoundError: java/lang/instrument/IllegalClassFormatException` or VM init errors**

**Cause**: The bundled `resources/jre` is not a full JRE 17 (trimmed or incorrect). Missing `lib/modules` or `java.instrument`.

**Fix**:
- Replace with a full JRE 17 distribution (Temurin/Adoptium ZIP), ensure `lib/modules` exists; or
- Remove `resources/jre` to fall back to system Java 17.

**Q2: `cargo tauri build` says `Couldn't find a .ico icon`**

**Fix**: Ensure `src-tauri/icons/icon.ico` exists and is referenced in `tauri.conf.json`:
`"icon": ["icons/icon.ico"]`.

**Q3: `glob pattern resources/jre/** not found`**

**Fix**: Use `"resources/jre"` instead of a glob.

**Q4: Unicode token errors during build**

**Cause**: Full-width punctuation or raw string literals closed unexpectedly.

**Fix**: Use ASCII punctuation and plain strings; use `'#status'` selector with normal strings.

**Q5: Program keeps spinning and never redirects**

**Checks**:
1. `netstat -ano | findstr :8082` to verify the port is listening.
2. Check `%APPDATA%/com.example.minigit/logs/launcher.log`.
3. Port may be in use or backend failed to start.

**Q6: `cargo tauri dev` shows `Waiting for dev server`**

**Cause**: Tauri v2 expects `devUrl`/dev server; this project uses static files.

**Fix**: Point `app.windows[0].url` to `index.html` and use `cargo tauri build`.

**Q7: Dependency/API mismatch**

**Fix**: Use the provided `main.rs` and stable APIs only; do not add undeclared dependencies (e.g., `reqwest`).

## 8. Optional improvements

- **JRE size optimization (jlink)**: Build a custom JRE 17 and replace `resources/jre`.
- **Configurable port/path**: Read environment variables such as `MINIGIT_PORT`, `MINIGIT_JAR` in `main.rs`.
- **Better logging**: Write key launcher states to `%APPDATA%` logs for troubleshooting.
- **Code signing**: Sign MSI/EXE to reduce SmartScreen warnings.

## 9. Release checklist

- [ ] `server.jar` placed in `src-tauri/resources/backend/`
- [ ] `resources/jre` is a full JRE 17 (or target machine has Java 17)
- [ ] `icons/icon.ico` exists
- [ ] `tauri.conf.json` passes JSON validation (no trailing commas)
- [ ] `cargo tauri build` succeeds
- [ ] Verify on a clean machine:
  - First run starts backend and opens UI
  - No extra console window (uses `javaw.exe` on Windows)
  - Uninstall removes all files

## 10. Summary of pitfalls

1. Missing VS C++ toolchain → `link.exe` not found.
2. Tauri v2 config misuse: `devPath/distDir` invalid; `identifier` missing; invalid JSON syntax.
3. Resource globbing: `resources/jre/**` fails on Windows; use the directory.
4. Webview API selection: avoid private APIs; use stable `get_webview_window` and `eval`.
5. String literals: avoid raw string conflicts; prefer normal strings and ASCII punctuation.
6. JRE version/integrity: use a full Java 17 runtime with `java.instrument` and `lib/modules`.
7. Readiness strategy: TCP probe is simple and dependency-free.

> The steps above have been verified on Windows 10 x64.
