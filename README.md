# Mini Git Server

一个轻量级的 Git 服务器，支持 Git Smart HTTP 协议，基于 Java 8 + Spring Boot + JGit 构建。

[![Java Version](https://img.shields.io/badge/Java-1.8+-blue.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-green.svg)](https://spring.io/projects/spring-boot)
[![JGit](https://img.shields.io/badge/JGit-5.13.3-orange.svg)](https://www.eclipse.org/jgit/)

## 🎯 特性

* ✅ **Git Smart HTTP 协议**：完全兼容标准 Git 客户端（包括 Eclipse EGit）
* ✅ **RESTful API**：简单的仓库管理接口
* ✅ **多语言支持**：中文 / 英文 / 日文国际化
* ✅ **HTTP Basic 认证**：简单可靠的身份验证
* ✅ **单文件部署**：打包为可执行 JAR，一键启动
* ✅ **操作审计**：详细的 Git 操作日志记录
* ✅ **健康检查**：监控服务状态
* ✅ **内置命令行客户端（mgit）**：无需安装原生 `git.exe` 即可进行常用操作（init/clone/status/add/commit/log/branch/checkout/push/pull/remote）

---

## 🚀 快速开始

### 环境要求

* Java 8 或更高版本
* Maven 3.6+（仅编译时需要）

### 编译构建

```bash
# 克隆项目
git clone <项目地址>
cd mini-git-server

# 编译打包（同时产出 server 与 client 两个可执行 JAR）
mvn clean package -DskipTests
```

**生成的 JAR 文件：**

* 服务端：`target/mini-git-server-1.0.0.jar`
* 命令行客户端（mgit）：`target/mini-git-server-1.0.0-client.jar`

> 说明：两个 JAR 都是 Spring Boot 可执行包；运行 `-client.jar` 时会启动你的 `com.minigit.util.GitClientMain` 作为入口。

### 启动服务

#### 基本启动

```bash
java -jar target/mini-git-server-1.0.0.jar
```

#### 自定义配置启动

```bash
java -jar target/mini-git-server-1.0.0.jar \
  --server.port=8080 \
  --vcs.storage.dir="/opt/git-repos" \
  --vcs.auth.user=admin \
  --vcs.auth.pass=mypassword \
  --vcs.lang.default=zh
```

### 服务验证

```bash
curl http://localhost:8080/actuator/health
```

---

## 📚 使用指南

### REST API

#### 1. 创建仓库

```bash
# 中文环境
curl -u admin:admin123 -H "Accept-Language: zh" -X POST \
  "http://localhost:8080/api/repos?name=my-project"

# 响应示例
{
  "name": "my-project.git"
}
```

#### 2. 列出仓库

```bash
curl -u admin:admin123 "http://localhost:8080/api/repos"

# 响应示例
[
  "my-project.git",
  "docs.git"
]
```

#### 3. 错误响应格式

```json
{
  "error": "REPO_ALREADY_EXISTS",
  "message": "仓库已存在: my-project.git",
  "timestamp": "2025-08-29T02:19:31Z"
}
```

### Git 操作

#### Eclipse EGit 使用

1. **克隆仓库**

    * 在 Eclipse 中选择 `File` → `Import` → `Git` → `Projects from Git`
    * 选择 `Clone URI`
    * 输入 URI：`http://localhost:8080/git/my-project.git`
    * 输入认证信息：`admin` / `admin123`

2. **推送代码**

    * 右键项目 → `Team` → `Push Branch`
    * 首次推送时会要求设置上游分支

#### 命令行使用（原生 git 示例）

```bash
# 克隆仓库（带凭证）
git clone http://admin:admin123@localhost:8080/git/my-project.git

# 或者分步操作（会提示输入密码）
git clone http://localhost:8080/git/my-project.git
cd my-project

# 添加文件并提交
echo "# My Project" > README.md
git add README.md
git commit -m "Initial commit"

# 推送到远程
git push origin main
```

---

## 🧰 命令行客户端（mgit）

> 适用于 **无法安装原生 git** 的场景。客户端内置于本项目，构建后可直接运行 `-client.jar` 完成常用 Git 操作。

### 启动与帮助

```bash
# Windows / macOS / Linux 通用
java -jar target/mini-git-server-1.0.0-client.jar help
```

### 命令一览（与原生 git 的映射）

| mgit 命令    | 语法                                                               | 对应原生 git                  |
| ---------- | ---------------------------------------------------------------- | ------------------------- |
| `init`     | `init <path>`                                                    | `git init`                |
| `clone`    | `clone <url> <dir> [--user U --pass P]`                          | `git clone`               |
| `status`   | `status <repoPath>`                                              | `git status`              |
| `add`      | `add <repoPath> <path1> [path2 ...]`                             | `git add`                 |
| `commit`   | `commit <repoPath> -m "message"`                                 | `git commit -m`           |
| `log`      | `log <repoPath> [--max N]`                                       | `git log`                 |
| `branch`   | `branch <repoPath>`（列出） / `branch -c <repoPath> <newBranch>`（创建） | `git branch`              |
| `checkout` | `checkout <repoPath> <branchOrCommit>`                           | `git checkout/switch`     |
| `remote`   | `remote -v <repoPath>`                                           | `git remote -v`           |
| `push`     | `push <repoPath> <remote> <ref> [--user U --pass P]`             | `git push <remote> <ref>` |
| `pull`     | `pull <repoPath> <remote> <ref> [--user U --pass P]`             | `git pull`（fetch+merge）   |

> 说明：当前 `mgit` 的命令形式以 `<repoPath>` 显式传入仓库路径（不使用全局 `-C` 参数）。

### 常见工作流示例

```bash
# 1) 初始化与首次提交
java -jar target/mini-git-server-1.0.0-client.jar init D:\repos\demo
echo hello > D:\repos\demo\README.md
java -jar target/mini-git-server-1.0.0-client.jar add D:\repos\demo README.md
java -jar target/mini-git-server-1.0.0-client.jar commit D:\repos\demo -m "first commit"

# 2) 配置远端（用原生 git 或手工编辑 .git/config）
cd /d D:\repos\demo
git config remote.origin.url http://localhost:8080/git/demo.git

# 3) 推送 / 拉取（支持 HTTP Basic）

# 方式 A：命令行显式用户名/密码（演示用途，生产不建议留历史）
java -jar target\mini-git-server-1.0.0-client.jar push D:\repos\demo origin master --user alice --pass 123456

# 方式 B：环境变量（推荐）
# Windows CMD:
set GIT_USER=alice
set GIT_PASSWORD=123456
# PowerShell:
$env:GIT_USER="alice"; $env:GIT_PASSWORD="123456"
# macOS/Linux:
export GIT_USER=alice; export GIT_PASSWORD=123456

java -jar target\mini-git-server-1.0.0-client.jar push D:\repos\demo origin master
java -jar target\mini-git-server-1.0.0-client.jar pull D:\repos\demo origin master
```

### 认证解析优先级

1. `--user/--pass`
2. 环境变量 `GIT_USER` / `GIT_PASSWORD`
3. 控制台交互输入（若前两者都未提供且控制台可用）

### 故障排除（mgit）

* **`Authentication failed`**：检查 `--user/--pass` 或环境变量是否正确；确认服务端账号密码与 URL。
* **`Repository not found`**：先通过 REST API 创建仓库或确认远程地址无误。
* **合并冲突**（`pull` 后状态为 `CONFLICTING` 等）：按提示在工作区解决冲突后，再次 `add/commit`。

---

## ✅ 支持的 Git 操作清单

* `git clone` - 克隆仓库
* `git fetch` - 获取更新
* `git pull` - 拉取并合并
* `git push` - 推送提交
* 分支操作
* 标签操作

---

## ⚙️ 配置说明

### 配置文件参数（`application.properties`）

```properties
# 服务器端口
server.port=8080

# 仓库存储目录
vcs.storage.dir=./data/repos

# 认证信息
vcs.auth.user=admin
vcs.auth.pass=admin123

# 默认语言 (en/zh/ja)
vcs.lang.default=en

# 日志级别
logging.level.com.minigit=INFO
```

### 命令行参数覆盖

```bash
java -jar mini-git-server-1.0.0.jar \
  --server.port=9090 \
  --vcs.storage.dir="D:\git-data" \
  --vcs.auth.user=gitadmin \
  --vcs.auth.pass=securepassword \
  --logging.level.com.minigit=DEBUG
```

---

## 📁 目录结构

```
./
├── data/                    # 默认数据目录
│   └── repos/              # Git 仓库存储目录
│       ├── project1.git/   # 裸仓库1
│       └── project2.git/   # 裸仓库2
├── logs/                   # 日志目录
│   ├── mini-git-server.log # 应用日志
│   └── git-access.log      # Git 操作审计日志
└── target/
    ├── mini-git-server-1.0.0.jar           # 服务端
    └── mini-git-server-1.0.0-client.jar    # 命令行客户端（mgit）
```

---

## 🔒 安全注意事项

### 默认配置安全风险

⚠️ **生产环境必须修改默认密码！**

```bash
# 生产环境启动示例
java -jar mini-git-server-1.0.0.jar \
  --vcs.auth.user=mygituser \
  --vcs.auth.pass=MyS3cur3P@ssw0rd \
  --vcs.storage.dir=/secure/git/repos
```

### 网络安全建议

* 在生产环境中，建议在反向代理（如 Nginx）后运行
* 启用 HTTPS 加密传输
* 限制访问 IP 范围
* 定期备份仓库数据

---

## 📊 监控和日志

### 健康检查

```bash
# 检查服务状态
curl http://localhost:8080/actuator/health

# 响应示例
{
  "status": "UP",
  "details": {
    "storage": "/path/to/repos",
    "repositories": 3,
    "version": "1.0.0"
  }
}
```

### 日志文件

* **应用日志**：`logs/mini-git-server.log`
* **Git 访问日志**：`logs/git-access.log`

### 日志示例

```
# Git 访问日志格式
2025-08-29 10:30:15.123 - OPERATION=CLONE REPO=my-project.git USER=admin IP=192.168.1.100 SUCCESS=true DURATION=234ms USER_AGENT=git/2.34.1
```

---

## 🔧 故障排除

### 常见问题

#### 1) 端口被占用

```bash
# 错误信息
Port 8080 was already in use

# 解决方案
java -jar mini-git-server-1.0.0.jar --server.port=8090
```

#### 2) 权限问题

```bash
# 错误信息
Cannot create storage directory

# 解决方案 - 确保目录权限
mkdir -p /opt/git-repos
chown -R $(whoami) /opt/git-repos
java -jar mini-git-server-1.0.0.jar --vcs.storage.dir=/opt/git-repos
```

#### 3) Git 克隆失败

```bash
# 错误信息
fatal: Authentication failed

# 解决方案 - 检查认证信息
git clone http://admin:admin123@localhost:8080/git/repo-name.git
```

#### 4) 仓库不存在

```bash
# 错误信息
Repository not found

# 解决方案 - 先创建仓库
curl -u admin:admin123 -X POST "http://localhost:8080/api/repos?name=repo-name"
```

### 调试模式

```bash
java -jar mini-git-server-1.0.0.jar \
  --logging.level.com.minigit=DEBUG \
  --logging.level.org.eclipse.jgit=INFO
```

---

## 🚀 部署建议

### Windows 服务部署

使用 NSSM 将应用注册为 Windows 服务：

```cmd
:: 下载 NSSM 并安装服务
nssm install MiniGitServer "C:\Program Files\Java\jdk1.8.0_XXX\bin\java.exe"
nssm set MiniGitServer Parameters "-jar D:\mini-git-server\mini-git-server-1.0.0.jar --vcs.storage.dir=D:\git-repos"
nssm start MiniGitServer
```

### Linux systemd 部署

创建服务文件 `/etc/systemd/system/mini-git-server.service`：

```ini
[Unit]
Description=Mini Git Server
After=network.target

[Service]
Type=simple
User=git
ExecStart=/usr/bin/java -jar /opt/mini-git-server/mini-git-server-1.0.0.jar --vcs.storage.dir=/opt/git-repos
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

启动服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable mini-git-server
sudo systemctl start mini-git-server
```

### Docker 部署 (可选)

创建 `Dockerfile`：

```dockerfile
FROM openjdk:8-jre-alpine
COPY target/mini-git-server-1.0.0.jar /app.jar
EXPOSE 8080
VOLUME ["/data"]
ENTRYPOINT ["java", "-jar", "/app.jar", "--vcs.storage.dir=/data/repos"]
```

构建和运行：

```bash
docker build -t mini-git-server .
docker run -d -p 8080:8080 -v /host/git-data:/data mini-git-server
```

---

## 📝 版本信息

* **当前版本**: 1.0.0
* **构建时间**: 2025-08-29
* **Java 版本**: 1.8+
* **Spring Boot**: 2.7.18
* **JGit**: 5.13.3

## 🤝 贡献

欢迎提交 Issue 和 Pull Request 来改进这个项目。

## 📄 许可证

本项目采用 MIT 许可证。

---

**提示**: 这是一个轻量级 Git 服务器，适用于小团队内网使用。如需更多高级功能（如 Web 界面、细粒度权限控制、Git LFS 等），建议使用 GitLab、Gitea 等成熟解决方案。
