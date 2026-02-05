package com.minigit.controller;

import com.minigit.service.GitRepositoryService;
import com.minigit.service.RepositoryService;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

/**
 * Web admin UI controller.
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
        "c", "h", "cpp", "hpp", "cs", "kt", "swift", "php", "pl", "r", "scala", "clj", "hs", "lua",
        "vim", "conf", "cfg", "ini", "env", "dockerfile", "makefile", "cmake", "toml", "lock",
        "proto", "thrift", "graphql", "dart", "elm", "erlang", "ex", "exs", "fs", "fsx", "ml", "mli",
        "nim", "pas", "pp", "tcl", "vb", "vbs", "asm", "s", "m", "mm", "plist", "strings",
        "csv", "tsv", "psv", "dsv"  // Treat CSV-like data files as text.
    )));

    private static final Set<String> IMAGE_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "png", "jpg", "jpeg", "gif", "bmp", "svg", "webp", "avif"
    )));

    private static final Set<String> PDF_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Collections.singletonList("pdf")));

    private static final Set<String> WORD_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("doc", "docx")));
    private static final Set<String> EXCEL_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("xls", "xlsx")));  // CSV removed.
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

    public WebController(RepositoryService repositoryService,
                         GitRepositoryService gitRepositoryService,
                         MessageSource messageSource) {
        this.repositoryService = repositoryService;
        this.gitRepositoryService = gitRepositoryService;
        this.messageSource = messageSource;
    }

    /**
     * Home - redirect to repository list.
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/admin";
    }

    /**
     * Admin home - repository list.
     */
    @GetMapping("/admin")
    public String adminIndex(Model model, Principal principal) {
        try {
            List<String> repositories = repositoryService.listRepositories();
            model.addAttribute("repositories", repositories);
            model.addAttribute("username", principal.getName());
            model.addAttribute("repoCount", repositories.size());

            File storageDir = ((com.minigit.service.impl.RepositoryServiceImpl) repositoryService).getStorageDir();
            model.addAttribute("storageDir", storageDir.getAbsolutePath());
            return "admin/index";
        } catch (Exception e) {
            logger.error("Failed to load admin page", e);
            model.addAttribute("error", getMessage("internal.error"));
            return "error";
        }
    }

    /**
     * File preview.
     */
    @GetMapping("/admin/repo/{name}/file")
    public String previewFile(@PathVariable String name,
                              @RequestParam("path") String path,
                              @RequestParam(value = "branch", required = false) String branch,
                              Model model) {
        try {
            String normalizedName = repositoryService.normalizeRepositoryName(name);
            if (!repositoryService.repositoryExists(normalizedName)) {
                model.addAttribute("error", getMessage("repo.not.found", name));
                return "error";
            }

            File repoDir = repositoryService.getRepositoryPath(normalizedName);
            GitRepositoryService.FileInfo fileInfo = gitRepositoryService.getFileInfo(repoDir, branch, path);
            if (!"file".equals(fileInfo.getType())) {
                model.addAttribute("error", "Directory preview is not supported.");
                return "error";
            }

            byte[] content = gitRepositoryService.getFileContent(repoDir, branch, path);
            String detectedMime = detectMimeType(fileInfo.getName());
            // Use smart content detection instead of simple detection.
            String previewType = determinePreviewTypeWithContentDetection(fileInfo.getName(), detectedMime, content);
            String mimeType = guessMimeType(fileInfo.getName(), previewType, detectedMime);

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
            String query = "path=" + encodedPath + (branch != null && !branch.isEmpty() ? "&branch=" + encodeUrlParam(branch) : "");
            model.addAttribute("downloadUrl", "/admin/repo/" + normalizedName + "/file/download?" + query);
            model.addAttribute("rawUrl", "/admin/repo/" + normalizedName + "/file/raw?" + query);

            StringBuilder backUrl = new StringBuilder("/admin/repo/").append(normalizedName);
            String parent = getParentPath(path);
            if (branch != null && !branch.isEmpty()) {
                backUrl.append("?branch=").append(encodeUrlParam(branch));
                if (!parent.isEmpty()) backUrl.append("&path=").append(encodeUrlParam(parent));
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
                    model.addAttribute("imageData", "data:" + (mimeType != null ? mimeType : "image/*") + ";base64," + java.util.Base64.getEncoder().encodeToString(content));
                } else if ("pdf".equals(previewType)) {
                    model.addAttribute("pdfData", "data:" + (mimeType != null ? mimeType : "application/pdf") + ";base64," + java.util.Base64.getEncoder().encodeToString(content));
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
            model.addAttribute("error", "Error loading file preview: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Raw file content.
     */
    @GetMapping("/admin/repo/{name}/file/raw")
    public ResponseEntity<byte[]> rawFile(@PathVariable String name,
                                          @RequestParam("path") String path,
                                          @RequestParam(value = "branch", required = false) String branch) {
        return serveFileContent(name, path, branch, false);
    }

    /**
     * File download.
     */
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
            String detectedMime = detectMimeType(fileInfo.getName());
            String previewType = determinePreviewType(fileInfo.getName(), detectedMime);
            String mimeType = guessMimeType(fileInfo.getName(), previewType, detectedMime);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mimeType != null ? MediaType.parseMediaType(mimeType) : MediaType.APPLICATION_OCTET_STREAM);
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
            if (MARKDOWN_EXTENSIONS.contains(extension)) return "markdown";
            if (TEXT_EXTENSIONS.contains(extension)) return "text";
            if (IMAGE_EXTENSIONS.contains(extension)) return "image";
            if (PDF_EXTENSIONS.contains(extension)) return "pdf";
            if (WORD_EXTENSIONS.contains(extension)) return "word";
            if (EXCEL_EXTENSIONS.contains(extension)) return "excel";
            if (POWERPOINT_EXTENSIONS.contains(extension)) return "powerpoint";
        }
        if (mimeType != null) {
            if (mimeType.startsWith("text/")) return "text";
            if (mimeType.startsWith("image/")) return "image";
            if ("application/pdf".equals(mimeType)) return "pdf";
        }
        return "binary";
    }

    /**
     * Smart detection of file content type, allowing text-like content to be treated as text.
     */
    private String determinePreviewTypeWithContentDetection(String fileName, String mimeType, byte[] content) {
        // First detect by extension and MIME type.
        String previewType = determinePreviewType(fileName, mimeType);

        // If detected as binary, try smart text detection.
        if ("binary".equals(previewType) && content != null && content.length > 0) {
            if (isLikelyTextContent(content)) {
                // Detected text-like content; downgrade to text preview.
                return "text";
            }
        }

        return previewType;
    }

    /**
     * Smart detection for text content.
     */
    private boolean isLikelyTextContent(byte[] content) {
        if (content == null || content.length == 0) return false;

        // Check the first few KB.
        int checkLength = Math.min(content.length, 8192);
        int controlChars = 0;
        int printableChars = 0;
        int nullBytes = 0;

        for (int i = 0; i < checkLength; i++) {
            int b = content[i] & 0xFF;

            // Check for null bytes (strong indicator of binary).
            if (b == 0) {
                nullBytes++;
                if (nullBytes > 3) return false; // Multiple null bytes likely indicate binary.
            }

            // Printable ASCII or common UTF-8 bytes.
            if ((b >= 0x20 && b <= 0x7E) || // Printable ASCII
                b == 0x09 || b == 0x0A || b == 0x0D || // Tab/newline/carriage return
                (b >= 0x80 && b <= 0xFF)) { // Likely UTF-8 bytes
                printableChars++;
            } else if (b < 0x20 && b != 0x09 && b != 0x0A && b != 0x0D) {
                controlChars++;
            }
        }

        // Treat as text if most characters are printable and few are control chars.
        double printableRatio = (double) printableChars / checkLength;
        double controlRatio = (double) controlChars / checkLength;

        return printableRatio > 0.7 && controlRatio < 0.1 && nullBytes <= 2;
    }

    private String detectHighlightLanguage(String fileName) {
        String extension = extractExtension(fileName);
        return extension == null ? null : HIGHLIGHT_LANGUAGE_MAP.get(extension);
    }

    private boolean shouldRenderAsText(String previewType) {
        return "text".equals(previewType) || "markdown".equals(previewType);
    }

    private boolean isLikelyBinary(byte[] content) {
        int controlChars = 0;
        int length = Math.min(content.length, 4096);
        for (int i = 0; i < length; i++) {
            int b = content[i] & 0xFF;
            if (b == 0) return true;
            if (b < 0x09 || (b > 0x0D && b < 0x20)) controlChars++;
        }
        return controlChars > length / 8;
    }

    private String detectMimeType(String fileName) {
        if (fileName == null) return null;
        try {
            return URLConnection.getFileNameMap().getContentTypeFor(fileName);
        } catch (Exception e) {
            logger.debug("Failed to detect mime type for {} via URLConnection: {}", fileName, e.getMessage());
            return null;
        }
    }

    private String guessMimeType(String fileName, String previewType, String detectedMime) {
        String extension = extractExtension(fileName);
        if ("markdown".equals(previewType)) return "text/markdown;charset=UTF-8";
        if ("text".equals(previewType)) return "text/plain;charset=UTF-8";
        if ("image".equals(previewType)) {
            if ("svg".equals(extension)) return "image/svg+xml";
            if (extension != null) return "image/" + extension.replace("jpg", "jpeg");
        }
        if ("pdf".equals(previewType)) return "application/pdf";
        if ("word".equals(previewType)) return "docx".equals(extension)
                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                : "application/msword";
        if ("excel".equals(previewType)) {
            if ("xlsx".equals(extension)) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            if ("csv".equals(extension)) return "text/csv;charset=UTF-8";
            return "application/vnd.ms-excel";
        }
        if ("powerpoint".equals(previewType)) return "pptx".equals(extension)
                ? "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                : "application/vnd.ms-powerpoint";
        if (detectedMime != null && !detectedMime.isEmpty()) return detectedMime;
        return detectMimeType(fileName);
    }

    private String buildContentDisposition(boolean attachment, String fileName) {
        String type = attachment ? "attachment" : "inline";
        String escaped = fileName.replace("\"", "\\\"");
        return type + "; filename=\"" + escaped + "\"; filename*=UTF-8''" + encodeFileName(fileName);
    }

    private String encodeFileName(String fileName) {
        try {
            return URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception e) {
            return fileName;
        }
    }

    private String encodeUrlParam(String value) {
        return value == null ? "" : encodeFileName(value);
    }

    private String extractExtension(String fileName) {
        if (fileName == null) return null;
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return null;
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String getParentPath(String path) {
        if (path == null || path.trim().isEmpty()) return "";
        String trimmed = path.trim();
        int idx = trimmed.lastIndexOf('/');
        return (idx <= 0) ? "" : trimmed.substring(0, idx);
    }

    /**
     * Create a new branch.
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
            redirectAttributes.addFlashAttribute("success", "Branch created successfully: " + newBranch);
            return "redirect:/admin/repo/" + normalizedName + "?branch=" + newBranch;
        } catch (Exception e) {
            logger.error("Failed to create branch {} from {}", newBranch, fromBranch, e);
            redirectAttributes.addFlashAttribute("error", "Failed to create branch: " + e.getMessage());
            return "redirect:/admin/repo/" + name + "?branch=" + fromBranch;
        }
    }

    /**
     * Repository creation page.
     */
    @GetMapping("/admin/create")
    public String createRepoPage(Model model) {
        return "admin/create";
    }

    /**
     * Handle repository creation.
     */
    @PostMapping("/admin/create")
    public String createRepo(@RequestParam String name, RedirectAttributes redirectAttributes) {
        try {
            if (name == null || name.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", getMessage("validation.name.required"));
                return "redirect:/admin/create";
            }
            if (!repositoryService.isValidRepositoryName(name)) {
                redirectAttributes.addFlashAttribute("error", getMessage("validation.name.invalid"));
                return "redirect:/admin/create";
            }
            String normalizedName = repositoryService.normalizeRepositoryName(name);
            if (repositoryService.repositoryExists(normalizedName)) {
                redirectAttributes.addFlashAttribute("error", getMessage("repo.exists", normalizedName));
                return "redirect:/admin/create";
            }
            repositoryService.createRepository(name);
            redirectAttributes.addFlashAttribute("success", getMessage("repo.created", normalizedName));
            return "redirect:/admin";
        } catch (Exception e) {
            logger.error("Failed to create repository via web: {}", name, e);
            redirectAttributes.addFlashAttribute("error", getMessage("internal.error"));
            return "redirect:/admin/create";
        }
    }

    /**
     * Repository details.
     */
    @GetMapping("/admin/repo/{name}")
    public String repoDetail(@PathVariable String name,
                             @RequestParam(value = "branch", required = false) String branch,
                             @RequestParam(value = "path", required = false) String path,
                             @RequestParam(value = "debug", required = false) boolean debug,
                             Model model,
                             HttpServletRequest request) {
        String normalizedName;
        File repoDir;
        try {
            normalizedName = repositoryService.normalizeRepositoryName(name);
            if (!repositoryService.repositoryExists(normalizedName)) {
                model.addAttribute("error", getMessage("repo.not.found", normalizedName));
                return "error";
            }
            repoDir = repositoryService.getRepositoryPath(normalizedName);

            model.addAttribute("repoName", normalizedName);
            model.addAttribute("repoPath", repoDir.getAbsolutePath());
            model.addAttribute("cloneUrl", getCloneUrl(normalizedName, request));
            model.addAttribute("repoSize", getDirectorySize(repoDir));

            boolean isEmpty;
            try {
                // Force refresh repository status.
                try (Git git = Git.open(repoDir)) {
                    git.getRepository().getRefDatabase().refresh();
                }
                isEmpty = gitRepositoryService.isEmptyRepository(repoDir);
            } catch (Exception e) {
                isEmpty = true;
                model.addAttribute("gitError", "Error checking repository status: " + e.getMessage());
            }
            model.addAttribute("isEmpty", isEmpty);
            if (debug) model.addAttribute("debugMode", true);

            model.addAttribute("branches", null);
            model.addAttribute("commits", null);
            model.addAttribute("files", null);
            model.addAttribute("currentBranch", null);

            String currentPath = path == null ? "" : path;
            model.addAttribute("currentPath", currentPath);

            List<Map<String, String>> breadcrumbs = new ArrayList<>();
            Map<String, String> rootCrumb = new HashMap<>();
            rootCrumb.put("name", "root");
            rootCrumb.put("path", "");
            breadcrumbs.add(rootCrumb);
            if (!currentPath.isEmpty()) {
                String[] parts = currentPath.split("/");
                StringBuilder builder = new StringBuilder();
                for (String part : parts) {
                    if (builder.length() > 0) builder.append('/');
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
                List<GitRepositoryService.BranchInfo> branches = null;
                try {
                    branches = gitRepositoryService.getBranches(repoDir);
                    model.addAttribute("branches", branches);
                } catch (Exception e) {
                    model.addAttribute("gitError", "Failed to load branch info: " + e.getMessage());
                }

                String currentBranch = branch;
                if (currentBranch == null && branches != null && !branches.isEmpty()) {
                    currentBranch = branches.stream()
                        .filter(GitRepositoryService.BranchInfo::isDefault)
                        .map(GitRepositoryService.BranchInfo::getShortName)
                        .findFirst()
                        .orElse(branches.get(0).getShortName());
                }
                model.addAttribute("currentBranch", currentBranch);

                if (currentBranch != null) {
                    try {
                        List<GitRepositoryService.CommitInfo> commits = gitRepositoryService.getCommitLog(repoDir, currentBranch, 20);
                        model.addAttribute("commits", commits);
                    } catch (Exception e) {
                        if (model.getAttribute("gitError") == null) {
                            model.addAttribute("gitError", "Failed to load commit history: " + e.getMessage());
                        }
                    }

                    try {
                        List<GitRepositoryService.FileInfo> files = gitRepositoryService.getFileList(repoDir, currentBranch, path);
                        model.addAttribute("files", files);
                    } catch (Exception e) {
                        if (model.getAttribute("gitError") == null) {
                            model.addAttribute("gitError", "Failed to load file list: " + e.getMessage());
                        }
                    }
                }
            }

            model.addAttribute("debugUrl", "/debug/git/repo/" + normalizedName);
            return "admin/detail";
        } catch (Exception e) {
            model.addAttribute("error", "Error loading repository details: " + e.getMessage());
            return "error";
        }
    }

    /**
     * System info page.
     */
    @GetMapping("/admin/system")
    public String systemInfo(Model model) {
        try {
            // System info.
            model.addAttribute("javaVersion", System.getProperty("java.version"));
            model.addAttribute("osName", System.getProperty("os.name"));
            model.addAttribute("osVersion", System.getProperty("os.version"));

            // Memory info.
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            model.addAttribute("maxMemory", formatBytes(maxMemory));
            model.addAttribute("totalMemory", formatBytes(totalMemory));
            model.addAttribute("usedMemory", formatBytes(usedMemory));
            model.addAttribute("freeMemory", formatBytes(freeMemory));

            // Storage info.
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
     * Get localized message.
     */
    private String getMessage(String key, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(key, args, key, locale);
    }

    /**
     * Build base URL from request, respecting reverse-proxy headers.
     */
    private String getBaseUrl(HttpServletRequest request) {
        // Prefer X-Forwarded-* headers.
        String scheme = getFirstHeader(request, "X-Forwarded-Proto");
        if (scheme == null || scheme.isEmpty()) scheme = request.getScheme();

        String forwardedHost = getFirstHeader(request, "X-Forwarded-Host");
        String host;
        if (forwardedHost != null && !forwardedHost.isEmpty()) {
            host = forwardedHost;
        } else {
            // Host header may include port.
            String hostHeader = request.getHeader("Host");
            if (hostHeader != null && !hostHeader.isEmpty()) {
                host = hostHeader;
            } else {
                host = request.getServerName();
                int serverPort = request.getServerPort();
                if (serverPort > 0) host = host + ":" + serverPort;
            }
        }

        // Parse host and port, support [IPv6]:port format.
        String hostname;
        Integer portFromHost = null;
        if (host.startsWith("[")) {
            int rb = host.indexOf(']');
            if (rb > 0) {
                hostname = host.substring(1, rb);
                if (rb + 1 < host.length() && host.charAt(rb + 1) == ':') {
                    try { portFromHost = Integer.parseInt(host.substring(rb + 2)); } catch (NumberFormatException ignored) {}
                }
            } else {
                // Incomplete IPv6; fall back to raw host.
                hostname = host;
            }
        } else {
            int colon = host.lastIndexOf(":");
            if (colon > -1 && colon < host.length() - 1) {
                hostname = host.substring(0, colon);
                try { portFromHost = Integer.parseInt(host.substring(colon + 1)); } catch (NumberFormatException e) { hostname = host; }
            } else {
                hostname = host;
            }
        }

        int port = -1;
        String portHeader = getFirstHeader(request, "X-Forwarded-Port");
        if (portHeader != null) {
            try { port = Integer.parseInt(portHeader); } catch (NumberFormatException ignored) {}
        }
        if (port <= 0) port = (portFromHost != null) ? portFromHost : request.getServerPort();

        boolean isStandard = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        StringBuilder base = new StringBuilder();
        base.append(scheme).append("://");
        // Add brackets for IPv6 when rendering.
        if (hostname != null && hostname.contains(":") && !hostname.startsWith("[")) {
            base.append('[').append(hostname).append(']');
        } else {
            base.append(hostname);
        }
        if (!isStandard && port > 0) base.append(":").append(port);
        return base.toString();
    }

    private String getFirstHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null) return null;
        int comma = value.indexOf(',');
        return comma > 0 ? value.substring(0, comma).trim() : value.trim();
    }

    /**
     * Get clone URL.
     */
    private String getCloneUrl(String repoName, HttpServletRequest request) {
        String base = getBaseUrl(request);
        return base + "/git/" + repoName;
    }

    /**
     * Calculate directory size.
     */
    private long getDirectorySize(File directory) {
        long size = 0;
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) size += file.length();
                    else if (file.isDirectory()) size += getDirectorySize(file);
                }
            }
        }
        return size;
    }

    /**
     * Format byte size.
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        else if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        else if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        else return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
