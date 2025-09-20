package com.minigit.controller;

import com.minigit.service.GitRepositoryService;
import com.minigit.service.RepositoryService;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;

/**
 * Web管理界面控制器
 */
@Controller
@RequestMapping("/")
public class WebController {

    private static final Logger logger = LoggerFactory.getLogger(WebController.class);

    private static final Parser MARKDOWN_PARSER = Parser.builder().build();
    private static final HtmlRenderer MARKDOWN_RENDERER = HtmlRenderer.builder().build();

    private static final Set<String> MARKDOWN_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "md", "markdown", "mdown", "mkd"
    )));

    private static final Set<String> TEXT_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "txt", "log", "gitignore", "gitattributes", "java", "js", "ts", "css", "scss", "html", "xml",
        "json", "yml", "yaml", "properties", "gradle", "py", "rb", "go", "rs", "sh", "bat", "sql",
        "c", "h", "cpp", "hpp", "cs", "kt", "swift"
    )));

    private static final Set<String> IMAGE_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "png", "jpg", "jpeg", "gif", "bmp", "svg", "webp", "avif"
    )));

    private static final Set<String> PDF_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Collections.singletonList("pdf")));

    private static final Set<String> WORD_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("doc", "docx")));
    private static final Set<String> EXCEL_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("xls", "xlsx", "csv")));
    private static final Set<String> POWERPOINT_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("ppt", "pptx")));

    private static final Map<String, String> HIGHLIGHT_LANGUAGE_MAP;

    private static final int MAX_INLINE_PREVIEW_BYTES = 1_048_576; // 1 MB

    static {
        Map<String, String> languageMap = new HashMap<>();
        languageMap.put("java", "java");
        languageMap.put("js", "javascript");
        languageMap.put("ts", "typescript");
        languageMap.put("css", "css");
        languageMap.put("scss", "scss");
        languageMap.put("html", "html");
        languageMap.put("xml", "xml");
        languageMap.put("json", "json");
        languageMap.put("yml", "yaml");
        languageMap.put("yaml", "yaml");
        languageMap.put("sh", "bash");
        languageMap.put("bash", "bash");
        languageMap.put("bat", "dos");
        languageMap.put("py", "python");
        languageMap.put("rb", "ruby");
        languageMap.put("go", "go");
        languageMap.put("rs", "rust");
        languageMap.put("c", "c");
        languageMap.put("h", "c");
        languageMap.put("cpp", "cpp");
        languageMap.put("hpp", "cpp");
        languageMap.put("cs", "csharp");
        languageMap.put("kt", "kotlin");
        languageMap.put("swift", "swift");
        languageMap.put("sql", "sql");
        HIGHLIGHT_LANGUAGE_MAP = Collections.unmodifiableMap(languageMap);
    }

    private final RepositoryService repositoryService;
    private final GitRepositoryService gitRepositoryService;
    private final MessageSource messageSource;
    private final Parser markdownParser;
    private final HtmlRenderer markdownRenderer;

    public WebController(RepositoryService repositoryService,
                        GitRepositoryService gitRepositoryService,
                        MessageSource messageSource) {
        this.repositoryService = repositoryService;
        this.gitRepositoryService = gitRepositoryService;
        this.messageSource = messageSource;
    }

    /**
     * 首页 - 重定向到仓库列表
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/admin";
    }

    /**
     * 管理首页 - 仓库列表
     */
    @GetMapping("/admin")
    public String adminIndex(Model model, Principal principal) {
        try {
            List<String> repositories = repositoryService.listRepositories();
            
            model.addAttribute("repositories", repositories);
            model.addAttribute("username", principal.getName());
            model.addAttribute("repoCount", repositories.size());
            
            // 获取存储目录信息
            File storageDir = ((com.minigit.service.impl.RepositoryServiceImpl) repositoryService).getStorageDir();
            model.addAttribute("storageDir", storageDir.getAbsolutePath());
            
            return "admin/index";
        } catch (Exception e) {
            logger.error("Failed to load admin page", e);
            model.addAttribute("error", getMessage("internal.error"));
            return "error";
        }
    }

    @GetMapping("/admin/repo/{name}/file")
    public String previewFile(@PathVariable String name,
                              @RequestParam("path") String path,
                              @RequestParam(value = "branch", required = false) String branch,
                              Model model) {
        String normalizedName;
        File repoDir;

        try {
            normalizedName = repositoryService.normalizeRepositoryName(name);
            if (!repositoryService.repositoryExists(normalizedName)) {
                model.addAttribute("error", getMessage("repo.not.found", name));
                return "error";
            }

            repoDir = repositoryService.getRepositoryPath(normalizedName);
            GitRepositoryService.FileInfo fileInfo = gitRepositoryService.getFileInfo(repoDir, branch, path);
            if (!"file".equals(fileInfo.getType())) {
                model.addAttribute("error", "目录不支持在线预览");
                return "error";
            }

            byte[] content = gitRepositoryService.getFileContent(repoDir, branch, path);
            String previewType = determinePreviewType(fileInfo.getName(), null);
            String mimeType = guessMimeType(fileInfo.getName(), previewType);

            boolean tooLargeForInline = shouldRenderAsText(previewType) && content.length > MAX_INLINE_PREVIEW_BYTES;
            boolean binaryDetected = shouldRenderAsText(previewType) && isLikelyBinary(content);
            boolean inlinePreview = !tooLargeForInline && !binaryDetected && previewType != null && !"binary".equals(previewType);

            model.addAttribute("repoName", normalizedName);
            model.addAttribute("branch", branch);
            model.addAttribute("fileName", fileInfo.getName());
            model.addAttribute("filePath", fileInfo.getPath());
            model.addAttribute("fileSize", fileInfo.getSize());
            model.addAttribute("fileSizeFormatted", fileInfo.getSizeFormatted());
            model.addAttribute("previewType", previewType);
            model.addAttribute("inlinePreview", inlinePreview);
            model.addAttribute("mimeType", mimeType);
            model.addAttribute("highlightLanguage", detectHighlightLanguage(fileInfo.getName()));
            model.addAttribute("tooLargeForInline", tooLargeForInline);
            model.addAttribute("binaryDetected", binaryDetected);

            String encodedPath = encodeUrlParam(path);
            String query = "path=" + encodedPath;
            if (branch != null && !branch.isEmpty()) {
                query += "&branch=" + encodeUrlParam(branch);
            }
            model.addAttribute("downloadUrl", "/admin/repo/" + normalizedName + "/file/download?" + query);
            model.addAttribute("rawUrl", "/admin/repo/" + normalizedName + "/file/raw?" + query);

            StringBuilder backUrl = new StringBuilder("/admin/repo/").append(normalizedName);
            String parent = getParentPath(path);
            if (branch != null && !branch.isEmpty()) {
                backUrl.append("?branch=").append(encodeUrlParam(branch));
                if (!parent.isEmpty()) {
                    backUrl.append("&path=").append(encodeUrlParam(parent));
                }
            } else if (!parent.isEmpty()) {
                backUrl.append("?path=").append(encodeUrlParam(parent));
            }
            model.addAttribute("backUrl", backUrl.toString());

            if (inlinePreview) {
                if ("markdown".equals(previewType)) {
                    String markdown = new String(content, StandardCharsets.UTF_8);
                    Node document = MARKDOWN_PARSER.parse(markdown);
                    String html = MARKDOWN_RENDERER.render(document);
                    model.addAttribute("markdownHtml", html);
                } else if ("text".equals(previewType)) {
                    model.addAttribute("textContent", new String(content, StandardCharsets.UTF_8));
                } else if ("image".equals(previewType)) {
                    model.addAttribute("imageData", "data:" + (mimeType != null ? mimeType : "image/*") + ";base64," + Base64.getEncoder().encodeToString(content));
                } else if ("pdf".equals(previewType)) {
                    model.addAttribute("pdfData", "data:" + (mimeType != null ? mimeType : "application/pdf") + ";base64," + Base64.getEncoder().encodeToString(content));
                }
            }

            boolean requiresClientRender = Arrays.asList("word", "excel", "powerpoint").contains(previewType);
            model.addAttribute("clientRenderOffice", requiresClientRender);
            model.addAttribute("previewAvailable", inlinePreview || requiresClientRender);

            return "admin/file-viewer";
        } catch (IllegalArgumentException e) {
            logger.warn("File preview failed for repo {} path {}: {}", name, path, e.getMessage());
            model.addAttribute("error", e.getMessage());
            return "error";
        } catch (Exception e) {
            logger.error("Unexpected error preparing file preview for repo {} path {}", name, path, e);
            model.addAttribute("error", "加载文件预览时发生错误: " + e.getMessage());
            return "error";
        }
    }

    @GetMapping("/admin/repo/{name}/file/raw")
    public ResponseEntity<byte[]> rawFile(@PathVariable String name,
                                          @RequestParam("path") String path,
                                          @RequestParam(value = "branch", required = false) String branch) {
        return serveFileContent(name, path, branch, false);
    }

    @GetMapping("/admin/repo/{name}/file/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String name,
                                               @RequestParam("path") String path,
                                               @RequestParam(value = "branch", required = false) String branch) {
        return serveFileContent(name, path, branch, true);
    }

    private ResponseEntity<byte[]> serveFileContent(String repoName, String path, String branch, boolean attachment) {
        try {
            String normalizedName = repositoryService.normalizeRepositoryName(repoName);
            if (!repositoryService.repositoryExists(normalizedName)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            File repoDir = repositoryService.getRepositoryPath(normalizedName);
            GitRepositoryService.FileInfo fileInfo = gitRepositoryService.getFileInfo(repoDir, branch, path);
            if (!"file".equals(fileInfo.getType())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            byte[] content = gitRepositoryService.getFileContent(repoDir, branch, path);
            String previewType = determinePreviewType(fileInfo.getName(), null);
            String mimeType = guessMimeType(fileInfo.getName(), previewType);

            HttpHeaders headers = new HttpHeaders();
            if (mimeType != null) {
                headers.setContentType(MediaType.parseMediaType(mimeType));
            } else {
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            }
            headers.set(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(attachment, fileInfo.getName()));
            headers.setContentLength(content.length);

            return new ResponseEntity<>(content, headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            logger.warn("Serve file failed for repo {} path {}: {}", repoName, path, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            logger.error("Error serving file {} from repo {}", path, repoName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String determinePreviewType(String fileName, String mimeType) {
        String extension = extractExtension(fileName);

        if (extension != null) {
            if (MARKDOWN_EXTENSIONS.contains(extension)) {
                return "markdown";
            }
            if (TEXT_EXTENSIONS.contains(extension)) {
                return "text";
            }
            if (IMAGE_EXTENSIONS.contains(extension)) {
                return "image";
            }
            if (PDF_EXTENSIONS.contains(extension)) {
                return "pdf";
            }
            if (WORD_EXTENSIONS.contains(extension)) {
                return "word";
            }
            if (EXCEL_EXTENSIONS.contains(extension)) {
                return "excel";
            }
            if (POWERPOINT_EXTENSIONS.contains(extension)) {
                return "powerpoint";
            }
        }

        if (mimeType != null) {
            if (mimeType.startsWith("text/")) {
                return "text";
            }
            if (mimeType.startsWith("image/")) {
                return "image";
            }
            if (mimeType.equals("application/pdf")) {
                return "pdf";
            }
        }

        return "binary";
    }

    private String detectHighlightLanguage(String fileName) {
        String extension = extractExtension(fileName);
        if (extension == null) {
            return null;
        }
        return HIGHLIGHT_LANGUAGE_MAP.get(extension);
    }

    private boolean shouldRenderAsText(String previewType) {
        return "text".equals(previewType) || "markdown".equals(previewType);
    }

    private boolean isLikelyBinary(byte[] content) {
        int controlChars = 0;
        int length = Math.min(content.length, 4096);
        for (int i = 0; i < length; i++) {
            int b = content[i] & 0xFF;
            if (b == 0) {
                return true;
            }
            if (b < 0x09 || (b > 0x0D && b < 0x20)) {
                controlChars++;
            }
        }
        return controlChars > length / 8;
    }

    private String guessMimeType(String fileName, String previewType) {
        String extension = extractExtension(fileName);
        if ("markdown".equals(previewType)) {
            return "text/markdown;charset=UTF-8";
        }
        if ("text".equals(previewType)) {
            return "text/plain;charset=UTF-8";
        }
        if ("image".equals(previewType)) {
            if ("svg".equals(extension)) {
                return "image/svg+xml";
            }
            if (extension != null) {
                return "image/" + extension.replace("jpg", "jpeg");
            }
        }
        if ("pdf".equals(previewType)) {
            return "application/pdf";
        }
        if ("word".equals(previewType)) {
            if ("docx".equals(extension)) {
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }
            return "application/msword";
        }
        if ("excel".equals(previewType)) {
            if ("xlsx".equals(extension)) {
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            }
            if ("csv".equals(extension)) {
                return "text/csv;charset=UTF-8";
            }
            return "application/vnd.ms-excel";
        }
        if ("powerpoint".equals(previewType)) {
            if ("pptx".equals(extension)) {
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            }
            return "application/vnd.ms-powerpoint";
        }

        String detected = URLConnection.getFileNameMap().getContentTypeFor(fileName);
        if (detected != null) {
            return detected;
        }
        return null;
    }

    private String buildContentDisposition(boolean attachment, String fileName) {
        String type = attachment ? "attachment" : "inline";
        String escaped = fileName.replace("\"", "\\\"");
        return type + "; filename=\"" + escaped + "\"; filename*=UTF-8''" + encodeFileName(fileName);
    }

    private String encodeFileName(String fileName) {
        try {
            return URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
        } catch (Exception e) {
            return fileName;
        }
    }

    private String encodeUrlParam(String value) {
        if (value == null) {
            return "";
        }
        return encodeFileName(value);
    }

    private String extractExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String getParentPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "";
        }
        String trimmed = path.trim();
        int idx = trimmed.lastIndexOf('/');
        if (idx <= 0) {
            return "";
        }
        return trimmed.substring(0, idx);
    }

    /**
     * 创建新分支
     */
    @PostMapping("/admin/repo/{name}/branch")
    public String createBranch(@PathVariable String name,
                               @RequestParam("fromBranch") String fromBranch,
                               @RequestParam("newBranch") String newBranch,
                               RedirectAttributes redirectAttributes) {
        try {
            String normalizedName = repositoryService.normalizeRepositoryName(name);
            File repoDir = repositoryService.getRepositoryPath(normalizedName);
            gitRepositoryService.createBranch(repoDir, fromBranch, newBranch);
            redirectAttributes.addFlashAttribute("success", "分支创建成功: " + newBranch);
            return "redirect:/admin/repo/" + normalizedName + "?branch=" + newBranch;
        } catch (Exception e) {
            logger.error("Failed to create branch {} from {}", newBranch, fromBranch, e);
            redirectAttributes.addFlashAttribute("error", "创建分支失败: " + e.getMessage());
            return "redirect:/admin/repo/" + name + "?branch=" + fromBranch;
        }
    }

    /**
     * 创建仓库页面
     */
    @GetMapping("/admin/create")
    public String createRepoPage(Model model) {
        return "admin/create";
    }

    /**
     * 处理创建仓库请求
     */
    @PostMapping("/admin/create")
    public String createRepo(@RequestParam String name, RedirectAttributes redirectAttributes) {
        try {
            logger.info("Creating repository via web: {}", name);
            
            // 验证仓库名称
            if (name == null || name.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", getMessage("validation.name.required"));
                return "redirect:/admin/create";
            }
            
            if (!repositoryService.isValidRepositoryName(name)) {
                redirectAttributes.addFlashAttribute("error", getMessage("validation.name.invalid"));
                return "redirect:/admin/create";
            }
            
            // 检查仓库是否已存在
            String normalizedName = repositoryService.normalizeRepositoryName(name);
            if (repositoryService.repositoryExists(normalizedName)) {
                redirectAttributes.addFlashAttribute("error", getMessage("repo.exists", normalizedName));
                return "redirect:/admin/create";
            }
            
            // 创建仓库
            File repoDir = repositoryService.createRepository(name);
            
            logger.info("Repository created successfully via web: {}", normalizedName);
            redirectAttributes.addFlashAttribute("success", getMessage("repo.created", normalizedName));
            
            return "redirect:/admin";
            
        } catch (Exception e) {
            logger.error("Failed to create repository via web: {}", name, e);
            redirectAttributes.addFlashAttribute("error", getMessage("internal.error"));
            return "redirect:/admin/create";
        }
    }

    /**
     * 仓库详情页面
     */
    @GetMapping("/admin/repo/{name}")
    public String repoDetail(@PathVariable String name,
                            @RequestParam(value = "branch", required = false) String branch,
                            @RequestParam(value = "path", required = false) String path,
                            @RequestParam(value = "debug", required = false) boolean debug,
                            Model model,
                            HttpServletRequest request) {

        String normalizedName = null;
        File repoDir = null;
        
        try {
            normalizedName = repositoryService.normalizeRepositoryName(name);
            logger.info("=== Loading repository detail for: {} ===", normalizedName);
            
            if (!repositoryService.repositoryExists(normalizedName)) {
                logger.warn("Repository not found: {}", normalizedName);
                model.addAttribute("error", getMessage("repo.not.found", normalizedName));
                return "error";
            }
            
            repoDir = repositoryService.getRepositoryPath(normalizedName);
            logger.info("Repository directory: {}", repoDir.getAbsolutePath());
            
            model.addAttribute("repoName", normalizedName);
            model.addAttribute("repoPath", repoDir.getAbsolutePath());
            model.addAttribute("cloneUrl", getCloneUrl(normalizedName, request));
            model.addAttribute("repoSize", getDirectorySize(repoDir));
            
            // 检查是否为空仓库
            boolean isEmpty;
            try {
                isEmpty = gitRepositoryService.isEmptyRepository(repoDir);
                logger.info("Repository {} is empty: {}", normalizedName, isEmpty);
            } catch (Exception e) {
                logger.error("Error checking if repository is empty", e);
                isEmpty = true;
                model.addAttribute("gitError", "检查仓库状态时出错: " + e.getMessage());
            }
            
            model.addAttribute("isEmpty", isEmpty);
            
            // 调试信息
            if (debug) {
                model.addAttribute("debugMode", true);
            }
            
            // 初始化集合，确保不为null
            model.addAttribute("branches", null);
            model.addAttribute("commits", null);
            model.addAttribute("files", null);
            model.addAttribute("currentBranch", null);

            String currentPath = path == null ? "" : path;
            model.addAttribute("currentPath", currentPath);

            // 构建面包屑导航数据
            List<Map<String, String>> breadcrumbs = new ArrayList<>();
            Map<String, String> rootCrumb = new HashMap<>();
            rootCrumb.put("name", "root");
            rootCrumb.put("path", "");
            breadcrumbs.add(rootCrumb);
            if (!currentPath.isEmpty()) {
                String[] parts = currentPath.split("/");
                StringBuilder builder = new StringBuilder();
                for (String part : parts) {
                    if (builder.length() > 0) {
                        builder.append('/');
                    }
                    builder.append(part);
                    Map<String, String> crumb = new HashMap<>();
                    crumb.put("name", part);
                    crumb.put("path", builder.toString());
                    breadcrumbs.add(crumb);
                }
            }
            model.addAttribute("breadcrumbs", breadcrumbs);

            String parentPath = "";
            if (!currentPath.isEmpty()) {
                int idx = currentPath.lastIndexOf('/');
                parentPath = idx > 0 ? currentPath.substring(0, idx) : "";
            }
            model.addAttribute("parentPath", parentPath);
            
            if (!isEmpty) {
                logger.info("=== Loading Git information for non-empty repository ===");
                
                // 获取分支列表
                List<GitRepositoryService.BranchInfo> branches = null;
                try {
                    logger.info("Step 1: Loading branches...");
                    branches = gitRepositoryService.getBranches(repoDir);
                    logger.info("SUCCESS: Found {} branches", branches != null ? branches.size() : 0);
                    
                    if (branches != null && !branches.isEmpty()) {
                        for (GitRepositoryService.BranchInfo branchInfo : branches) {
                            logger.info("  Branch: {} (default: {})", 
                                branchInfo.getShortName(), branchInfo.isDefault());
                        }
                    }
                    
                    model.addAttribute("branches", branches);
                } catch (Exception e) {
                    logger.error("FAILED: Error loading branches", e);
                    model.addAttribute("gitError", "加载分支信息失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    branches = null;
                }
                
                // 获取当前分支
                String currentBranch = branch;
                if (currentBranch == null && branches != null && !branches.isEmpty()) {
                    currentBranch = branches.stream()
                        .filter(GitRepositoryService.BranchInfo::isDefault)
                        .map(GitRepositoryService.BranchInfo::getShortName)
                        .findFirst()
                        .orElse(branches.get(0).getShortName());
                }
                logger.info("Current branch: {}", currentBranch);
                model.addAttribute("currentBranch", currentBranch);
                
                if (currentBranch != null) {
                    // 获取提交日志
                    try {
                        logger.info("Step 2: Loading commits for branch: {}", currentBranch);
                        List<GitRepositoryService.CommitInfo> commits = gitRepositoryService.getCommitLog(repoDir, currentBranch, 20);
                        logger.info("SUCCESS: Found {} commits", commits != null ? commits.size() : 0);
                        
                        if (commits != null && !commits.isEmpty()) {
                            GitRepositoryService.CommitInfo firstCommit = commits.get(0);
                            logger.info("  Latest commit: {} - {} by {}", 
                                firstCommit.getShortId(), firstCommit.getMessage(), firstCommit.getAuthor());
                        }
                        
                        model.addAttribute("commits", commits);
                    } catch (Exception e) {
                        logger.error("FAILED: Error loading commits", e);
                        if (model.getAttribute("gitError") == null) {
                            model.addAttribute("gitError", "加载提交历史失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                        }
                    }
                    
                    // 获取文件列表
                    try {
                        logger.info("Step 3: Loading files for branch: {}, path: {}", currentBranch, path);
                        List<GitRepositoryService.FileInfo> files = gitRepositoryService.getFileList(repoDir, currentBranch, path);
                        logger.info("SUCCESS: Found {} files", files != null ? files.size() : 0);
                        
                        if (files != null && !files.isEmpty()) {
                            logger.info("  Files:");
                            for (GitRepositoryService.FileInfo file : files) {
                                logger.info("    - {} ({}) - {}", file.getName(), file.getType(), file.getSizeFormatted());
                            }
                        }
                        
                        model.addAttribute("files", files);
                    } catch (Exception e) {
                        logger.error("FAILED: Error loading files", e);
                        if (model.getAttribute("gitError") == null) {
                            model.addAttribute("gitError", "加载文件列表失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                        }
                    }
                }
            }
            
            // 添加调试链接
            model.addAttribute("debugUrl", "/debug/git/repo/" + normalizedName);
            
            logger.info("=== Repository detail loading completed ===");
            return "admin/detail";
            
        } catch (Exception e) {
            logger.error("FATAL: Failed to load repository detail for: {}", name, e);
            model.addAttribute("error", "加载仓库详情时发生严重错误: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            
            // 添加调试信息
            if (normalizedName != null) {
                model.addAttribute("debugUrl", "/debug/git/repo/" + normalizedName);
            }
            if (repoDir != null) {
                model.addAttribute("repoPath", repoDir.getAbsolutePath());
            }
            
            return "error";
        }
    }

    @GetMapping("/admin/repo/{name}/file")
    public String viewFile(@PathVariable String name,
                           @RequestParam("path") String path,
                           @RequestParam(value = "branch", required = false) String branch,
                           Model model) {
        String normalizedName = repositoryService.normalizeRepositoryName(name);
        if (!repositoryService.repositoryExists(normalizedName)) {
            model.addAttribute("error", getMessage("repo.not.found", normalizedName));
            return "error";
        }

        File repoDir = repositoryService.getRepositoryPath(normalizedName);
        String normalizedPath = normalizeFilePath(path);

        model.addAttribute("repoName", normalizedName);
        model.addAttribute("path", normalizedPath);

        try {
            String resolvedBranch = resolveBranchForFile(repoDir, branch);
            if (resolvedBranch == null) {
                model.addAttribute("error", "未找到可用的分支用于预览文件");
                return "admin/file-viewer";
            }

            GitRepositoryService.FileInfo fileInfo = gitRepositoryService.getFileInfo(repoDir, resolvedBranch, normalizedPath);
            if (fileInfo == null || !"file".equals(fileInfo.getType())) {
                model.addAttribute("error", "无法预览目录: " + normalizedPath);
                return "admin/file-viewer";
            }

            String fileName = fileInfo.getName();
            String mimeType = guessMimeType(fileName);
            String previewType = determinePreviewType(fileName, mimeType);

            model.addAttribute("branch", resolvedBranch);
            model.addAttribute("fileName", fileName);
            model.addAttribute("fileSize", fileInfo.getSize());
            model.addAttribute("fileSizeFormatted", fileInfo.getSizeFormatted());
            model.addAttribute("previewType", previewType);
            model.addAttribute("mimeType", mimeType);

            if ("markdown".equals(previewType) || "text".equals(previewType)) {
                byte[] contentBytes = gitRepositoryService.getFileContent(repoDir, resolvedBranch, normalizedPath);
                String textContent = new String(contentBytes, StandardCharsets.UTF_8);
                if ("markdown".equals(previewType)) {
                    String html = markdownRenderer.render(markdownParser.parse(textContent));
                    model.addAttribute("markdownHtml", html);
                }
                model.addAttribute("textContent", textContent);
            }

            model.addAttribute("isPreviewSupported", !"binary".equals(previewType));
            return "admin/file-viewer";
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to preview file {}/{}: {}", normalizedName, normalizedPath, e.getMessage());
            model.addAttribute("error", e.getMessage());
            return "admin/file-viewer";
        } catch (Exception e) {
            logger.error("Failed to preview file {}/{}", normalizedName, normalizedPath, e);
            model.addAttribute("error", "加载文件内容失败: " + e.getMessage());
            return "admin/file-viewer";
        }
    }

    @GetMapping("/admin/repo/{name}/file/raw")
    public ResponseEntity<byte[]> rawFile(@PathVariable String name,
                                          @RequestParam("path") String path,
                                          @RequestParam(value = "branch", required = false) String branch) {
        return serveFileContent(name, path, branch, false);
    }

    @GetMapping("/admin/repo/{name}/file/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String name,
                                               @RequestParam("path") String path,
                                               @RequestParam(value = "branch", required = false) String branch) {
        return serveFileContent(name, path, branch, true);
    }

    /**
     * 系统信息页面
     */
    @GetMapping("/admin/system")
    public String systemInfo(Model model) {
        try {
            // 系统信息
            model.addAttribute("javaVersion", System.getProperty("java.version"));
            model.addAttribute("osName", System.getProperty("os.name"));
            model.addAttribute("osVersion", System.getProperty("os.version"));
            
            // 内存信息
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            model.addAttribute("maxMemory", formatBytes(maxMemory));
            model.addAttribute("totalMemory", formatBytes(totalMemory));
            model.addAttribute("usedMemory", formatBytes(usedMemory));
            model.addAttribute("freeMemory", formatBytes(freeMemory));
            
            // 存储信息
            File storageDir = ((com.minigit.service.impl.RepositoryServiceImpl) repositoryService).getStorageDir();
            model.addAttribute("storageDir", storageDir.getAbsolutePath());
            model.addAttribute("totalSpace", formatBytes(storageDir.getTotalSpace()));
            model.addAttribute("freeSpace", formatBytes(storageDir.getFreeSpace()));
            model.addAttribute("usedSpace", formatBytes(storageDir.getTotalSpace() - storageDir.getFreeSpace()));
            
            return "admin/system";
        } catch (Exception e) {
            logger.error("Failed to load system info", e);
            model.addAttribute("error", getMessage("internal.error"));
            return "error";
        }
    }

    /**
     * 获取国际化消息
     */
    private String getMessage(String key, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(key, args, key, locale);
    }

    /**
     * 根据请求获取基础URL，尊重反向代理头
     */
    private String getBaseUrl(HttpServletRequest request) {
        // 优先使用 X-Forwarded-* 头
        String scheme = getFirstHeader(request, "X-Forwarded-Proto");
        if (scheme == null || scheme.isEmpty()) {
            scheme = request.getScheme();
        }

        String forwardedHost = getFirstHeader(request, "X-Forwarded-Host");
        String host;
        if (forwardedHost != null && !forwardedHost.isEmpty()) {
            host = forwardedHost;
        } else {
            // Host 头可能包含端口
            String hostHeader = request.getHeader("Host");
            if (hostHeader != null && !hostHeader.isEmpty()) {
                host = hostHeader;
            } else {
                host = request.getServerName();
                int serverPort = request.getServerPort();
                if (serverPort > 0) {
                    host = host + ":" + serverPort;
                }
            }
        }

        // 解析 host 与 port，支持 [IPv6]:port 格式
        String hostname;
        Integer portFromHost = null;
        if (host.startsWith("[")) {
            int rb = host.indexOf(']');
            if (rb > 0) {
                hostname = host.substring(1, rb);
                if (rb + 1 < host.length() && host.charAt(rb + 1) == ':') {
                    try {
                        portFromHost = Integer.parseInt(host.substring(rb + 2));
                    } catch (NumberFormatException ignored) {}
                }
            } else {
                // 不完整的 IPv6 表示，兜底按原样处理
                hostname = host;
            }
        } else {
            int colon = host.lastIndexOf(":");
            if (colon > -1 && colon < host.length() - 1) {
                hostname = host.substring(0, colon);
                try {
                    portFromHost = Integer.parseInt(host.substring(colon + 1));
                } catch (NumberFormatException e) {
                    hostname = host; // 解析失败则回退
                }
            } else {
                hostname = host;
            }
        }

        int port = -1;
        String portHeader = getFirstHeader(request, "X-Forwarded-Port");
        if (portHeader != null) {
            try {
                port = Integer.parseInt(portHeader);
            } catch (NumberFormatException ignored) {}
        }
        if (port <= 0) {
            port = (portFromHost != null) ? portFromHost : request.getServerPort();
        }

        boolean isStandard = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        StringBuilder base = new StringBuilder();
        base.append(scheme).append("://");
        // 输出时对 IPv6 加[]
        if (hostname != null && hostname.contains(":") && !hostname.startsWith("[")) {
            base.append('[').append(hostname).append(']');
        } else {
            base.append(hostname);
        }
        if (!isStandard && port > 0) {
            base.append(":").append(port);
        }
        return base.toString();
    }

    private String getFirstHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null) return null;
        int comma = value.indexOf(',');
        return comma > 0 ? value.substring(0, comma).trim() : value.trim();
    }

    /**
     * 获取克隆URL
     */
    private String getCloneUrl(String repoName, HttpServletRequest request) {
        String base = getBaseUrl(request);
        return base + "/git/" + repoName;
    }

    /**
     * 计算目录大小
     */
    private long getDirectorySize(File directory) {
        long size = 0;
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        size += file.length();
                    } else if (file.isDirectory()) {
                        size += getDirectorySize(file);
                    }
                }
            }
        }
        return size;
    }

    /**
     * 格式化字节大小
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    private String determinePreviewType(String fileName, String mimeType) {
        String extension = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        }

        if (MARKDOWN_EXTENSIONS.contains(extension)) {
            return "markdown";
        }
        if (IMAGE_EXTENSIONS.contains(extension) || (mimeType != null && mimeType.startsWith("image/"))) {
            return "image";
        }
        if (PDF_EXTENSIONS.contains(extension) || "application/pdf".equalsIgnoreCase(mimeType)) {
            return "pdf";
        }
        if (TEXT_EXTENSIONS.contains(extension)) {
            return "text";
        }
        if (mimeType != null && (mimeType.startsWith("text/") || mimeType.contains("json") || mimeType.contains("xml"))) {
            return "text";
        }
        return "binary";
    }

    private String guessMimeType(String fileName) {
        String mimeType = URLConnection.guessContentTypeFromName(fileName);
        if (mimeType == null) {
            if (MARKDOWN_EXTENSIONS.contains(getExtension(fileName))) {
                return "text/markdown";
            }
            if (TEXT_EXTENSIONS.contains(getExtension(fileName))) {
                return "text/plain";
            }
        }
        return mimeType;
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private String normalizeFilePath(String path) {
        if (path == null) {
            return null;
        }
        return path.replaceAll("^/+", "");
    }

    private String resolveBranchForFile(File repoDir, String branch) throws Exception {
        if (branch != null && !branch.trim().isEmpty()) {
            return branch;
        }

        List<GitRepositoryService.BranchInfo> branches = gitRepositoryService.getBranches(repoDir);
        if (branches == null || branches.isEmpty()) {
            return null;
        }

        return branches.stream()
            .filter(GitRepositoryService.BranchInfo::isDefault)
            .map(GitRepositoryService.BranchInfo::getShortName)
            .findFirst()
            .orElse(branches.get(0).getShortName());
    }

    private ResponseEntity<byte[]> serveFileContent(String name, String path, String branch, boolean download) {
        String normalizedName = repositoryService.normalizeRepositoryName(name);
        if (!repositoryService.repositoryExists(normalizedName)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        File repoDir = repositoryService.getRepositoryPath(normalizedName);
        String normalizedPath = normalizeFilePath(path);

        try {
            String resolvedBranch = resolveBranchForFile(repoDir, branch);
            if (resolvedBranch == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            GitRepositoryService.FileInfo fileInfo = gitRepositoryService.getFileInfo(repoDir, resolvedBranch, normalizedPath);
            if (fileInfo == null || !"file".equals(fileInfo.getType())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            byte[] content = gitRepositoryService.getFileContent(repoDir, resolvedBranch, normalizedPath);
            String fileName = fileInfo.getName();
            String mimeType = guessMimeType(fileName);
            MediaType mediaType = parseMediaType(mimeType);

            ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(fileName, StandardCharsets.UTF_8)
                .build();

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(mediaType)
                .contentLength(content.length)
                .body(content);
        } catch (IllegalArgumentException e) {
            logger.warn("File access validation failed for {}/{}: {}", normalizedName, normalizedPath, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            logger.error("Failed to serve file {}/{}", normalizedName, normalizedPath, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private MediaType parseMediaType(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}

