# Mini Git Server

A lightweight Git server that supports the Git Smart HTTP protocol, built with Java 8, Spring Boot, and JGit.

[![Java Version](https://img.shields.io/badge/Java-1.8+-blue.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-green.svg)](https://spring.io/projects/spring-boot)
[![JGit](https://img.shields.io/badge/JGit-5.13.3-orange.svg)](https://www.eclipse.org/jgit/)

## 🎯 Features

* ✅ **Git Smart HTTP**: Fully compatible with standard Git clients (including Eclipse EGit)
* ✅ **RESTful API**: Repository management endpoints (create, list, delete)
* ✅ **Web Admin UI**: Browser-based admin panel for repository and branch management
* ✅ **File Browser**: Browse, preview (Markdown, code, images, PDF), and download files
* ✅ **Branch Management**: Create and delete branches from the web UI
* ✅ **Localization**: English / Chinese / Japanese UI and messages
* ✅ **HTTP Basic Auth**: Simple and reliable authentication
* ✅ **Single-jar deployment**: Package as an executable JAR and run
* ✅ **Audit logging**: Detailed Git access logs
* ✅ **Health check**: Service health monitoring at `/actuator/health`
* ✅ **Built-in CLI client (mgit)**: Common Git operations without native `git.exe` (init/clone/status/add/commit/log/branch/checkout/push/pull/remote)

---

## 🚀 Quick Start

### Requirements

* Java 8 or later
* Maven 3.6+ (build time only)

### Build

```bash
# Clone the repository
git clone <repo-url>
cd mini-git-server

# Build (server + client executable JARs)
mvn clean package -DskipTests
```

**Generated JARs:**

* Server: `target/mini-git-server-1.0.0.jar`
* CLI client (mgit): `target/mini-git-server-1.0.0-client.jar`

> Note: Both are Spring Boot executable JARs. Running `-client.jar` starts `com.minigit.util.GitClientMain`.

### Run the server

#### Basic

```bash
java -jar target/mini-git-server-1.0.0.jar
```

#### Custom configuration

```bash
java -jar target/mini-git-server-1.0.0.jar \
  --server.port=8080 \
  --vcs.storage.dir="/opt/git-repos" \
  --vcs.auth.user=admin \
  --vcs.auth.pass=mypassword \
  --vcs.lang.default=en
```

### Verify service

```bash
curl http://localhost:8082/actuator/health
```

---

## 📚 Usage Guide

### Web Admin UI

Navigate to `http://localhost:8082/admin` in your browser and log in with your configured credentials.

The admin panel lets you:
- **List** all repositories with their sizes
- **Create** new repositories
- **Delete** repositories (with confirmation)
- **Browse** the file tree, view commits, and manage branches
- **Preview** files inline: Markdown, source code, images, PDF, and Office documents
- **Create / delete** branches

### REST API

#### 1. Create repository

```bash
# English locale
curl -u admin:admin123 -H "Accept-Language: en" -X POST \
  "http://localhost:8082/api/repos?name=my-project"

# Response
{
  "name": "my-project.git"
}
```

#### 2. List repositories

```bash
curl -u admin:admin123 "http://localhost:8082/api/repos"

# Response
[
  "my-project.git",
  "docs.git"
]
```

#### 3. Delete repository

```bash
curl -u admin:admin123 -X DELETE \
  "http://localhost:8082/api/repos/my-project"

# Response: 204 No Content on success, 404 if not found
```

#### 4. Error response format

```json
{
  "error": "REPO_ALREADY_EXISTS",
  "message": "Repository already exists: my-project.git",
  "timestamp": "2025-08-29T02:19:31Z"
}
```

### Git operations

#### Eclipse EGit

1. **Clone repository**

    * In Eclipse: `File` → `Import` → `Git` → `Projects from Git`
    * Choose `Clone URI`
    * Enter URI: `http://localhost:8082/git/my-project.git`
    * Credentials: `admin` / `admin123`

2. **Push code**

    * Right-click project → `Team` → `Push Branch`
    * Set upstream branch on first push

#### CLI usage (native Git example)

```bash
# Clone with credentials
git clone http://admin:admin123@localhost:8082/git/my-project.git

# Or step-by-step (will prompt for password)
git clone http://localhost:8082/git/my-project.git

# Add and commit
cd my-project
# ... edit files
# Commit

git add .
git commit -m "init"

# Push
git push origin main
```

---

## 🧰 CLI client (mgit)

> Designed for environments where native Git is unavailable. The client is built into this project; after building, run the `-client.jar` to perform common Git operations.

### Start & help

```bash
# Windows / macOS / Linux
java -jar target/mini-git-server-1.0.0-client.jar --help
```

### Command mapping

| mgit command | Syntax | Native Git |
| --- | --- | --- |
| `init` | `init <repoPath>` | `git init` |
| `clone` | `clone <url> <dir> [--user U --pass P]` | `git clone` |
| `status` | `status <repoPath>` | `git status` |
| `add` | `add <repoPath> <pathspec>` | `git add` |
| `commit` | `commit <repoPath> -m "msg"` | `git commit` |
| `log` | `log <repoPath> [--max N]` | `git log` |
| `branch` | `branch <repoPath>` / `branch -c <repoPath> <newBranch>` | `git branch` |
| `checkout` | `checkout <repoPath> <branch>` | `git checkout` |
| `remote` | `remote <repoPath>` / `remote -a <repoPath> <name> <url>` | `git remote` |
| `push` | `push <repoPath> <remote> <branch> [--user U --pass P]` | `git push` |
| `pull` | `pull <repoPath> <remote> <branch> [--user U --pass P]` | `git pull` |

> Note: `mgit` explicitly passes `<repoPath>` instead of using the global `-C` flag.

### Common workflow

```bash
# 1) Initialize and first commit
java -jar target/mini-git-server-1.0.0-client.jar init ./demo
cd demo
# ... edit files
java -jar target/mini-git-server-1.0.0-client.jar add . .
java -jar target/mini-git-server-1.0.0-client.jar commit . -m "first commit"

# 2) Configure remote (native Git or edit .git/config)
# 3) Push / pull (HTTP Basic supported)

# Option A: explicit credentials (demo only; avoid in production history)
java -jar target/mini-git-server-1.0.0-client.jar push . origin main --user admin --pass admin123

# Option B: environment variables (recommended)
export GIT_USER=admin
export GIT_PASSWORD=admin123
java -jar target/mini-git-server-1.0.0-client.jar push . origin main
```

### Auth resolution priority

1. CLI flags `--user` / `--pass`
2. Environment variables `GIT_USER` / `GIT_PASSWORD`
3. Interactive prompt (when console is available)

### Troubleshooting (mgit)

* **`Authentication failed`**: Verify `--user/--pass` or environment variables; confirm server credentials.
* **`Repository not found`**: Create the repo via REST API or verify remote URL.
* **Merge conflicts** (`pull` shows `CONFLICTING`): resolve in workspace, then `add/commit`.

---

## ✅ Supported Git operations

* `git clone` - clone repository
* `git fetch` - fetch updates
* `git pull` - pull and merge
* `git push` - push commits
* Branch operations
* Tag operations

---

## ⚙️ Configuration

### application.properties

```properties
# Server port
server.port=8082

# Repository storage directory
vcs.storage.dir=./data/repos

# Credentials
vcs.auth.user=admin
vcs.auth.pass=admin123

# Default language (en/zh/ja)
vcs.lang.default=en

# Logging
logging.level.com.minigit=INFO
```

### Command-line overrides

```bash
java -jar target/mini-git-server-1.0.0.jar \
  --server.port=8080 \
  --vcs.storage.dir=/data/repos \
  --vcs.auth.user=git \
  --vcs.auth.pass=secret
```

---

## 📁 Directory structure

```
mini-git-server/
├── data/                    # default data directory
│   └── repos/               # Git repository storage
│       ├── project1.git/    # bare repository 1
│       └── project2.git/    # bare repository 2
├── logs/                    # log directory
│   ├── mini-git-server.log  # application log
│   └── git-access.log       # Git access audit log
└── target/
    ├── mini-git-server-1.0.0.jar           # server
    └── mini-git-server-1.0.0-client.jar    # CLI client (mgit)
```

---

## 🔒 Security notes

### Default configuration risks

⚠️ **Change the default password in production!**

```bash
java -jar target/mini-git-server-1.0.0.jar \
  --vcs.auth.user=git \
  --vcs.auth.pass=change-me
```

### Network security suggestions

* Run behind a reverse proxy (e.g., Nginx)
* Enable HTTPS
* Restrict allowed IP ranges
* Back up repository data regularly

---

## 📊 Monitoring & logs

### Health check

```bash
curl http://localhost:8082/actuator/health
```

### Log files

* **Application log**: `logs/mini-git-server.log`
* **Git access log**: `logs/git-access.log`

### Log example

```text
[2025-08-29 10:43:27] git-upload-pack: /git/my-project.git, user=admin, ip=127.0.0.1
```

---

## 🔧 Troubleshooting

### 1) Port already in use

**Error**

```
Port 8080 was already in use.
```

**Fix**

* Change `server.port` or stop the process using the port.

### 2) Permission issues

**Error**

```
Permission denied: ./data/repos
```

**Fix**

* Ensure the directory is writable by the current user.

### 3) Git clone fails

**Error**

```
Authentication failed for 'http://localhost:8082/git/my-project.git/'
```

**Fix**

* Check credentials and server configuration.

### 4) Repository not found

**Error**

```
Repository not found: my-project.git
```

**Fix**

* Create the repo via REST API first, or verify the URL.

### Debug mode

* Start the server with `--debug`.

---

## 🚀 Deployment

### Windows service

Use NSSM to register the application as a service:

```
# Download NSSM and install
```

### Linux systemd

Create `/etc/systemd/system/mini-git-server.service`:

```ini
[Unit]
Description=Mini Git Server
After=network.target

[Service]
Type=simple
User=git
WorkingDirectory=/opt/mini-git-server
ExecStart=/usr/bin/java -jar /opt/mini-git-server/mini-git-server-1.0.0.jar
Restart=always

[Install]
WantedBy=multi-user.target
```

Start the service:

```bash
sudo systemctl daemon-reload
sudo systemctl enable mini-git-server
sudo systemctl start mini-git-server
```

### Docker (optional)

Create `Dockerfile`:

```dockerfile
FROM eclipse-temurin:8-jre
WORKDIR /app
COPY target/mini-git-server-1.0.0.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Build and run:

```bash
docker build -t mini-git-server .
docker run -d -p 8080:8080 -v ./data:/app/data mini-git-server
```

---

## 📝 Version info

* **Current version**: 1.0.0
* **Build time**: 2025-08-29
* **Java version**: 1.8+

---

## 🤝 Contributing

Issues and pull requests are welcome.

---

## 📄 License

This project is released under the MIT License.

---

**Tip**: This is a lightweight Git server for small teams or internal networks. For advanced features (web UI, fine-grained permissions, Git LFS, etc.), consider GitLab, Gitea, or other mature solutions.
