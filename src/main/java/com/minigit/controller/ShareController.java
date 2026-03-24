package com.minigit.controller;

import com.minigit.dto.ShareLink;
import com.minigit.service.GitRepositoryService;
import com.minigit.service.RepositoryService;
import com.minigit.service.ShareLinkService;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Handles public file share links (/share/**) and authenticated share management (/admin/share/**).
 */
@Controller
public class ShareController {

    private static final Logger logger = LoggerFactory.getLogger(ShareController.class);

    private static final int MAX_INLINE_PREVIEW_BYTES = 1_048_576;
    private static final int UTF8_VALIDATION_BUFFER_SIZE = 65536;

    private static final Set<String> MARKDOWN_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "md", "markdown", "mdown", "mkd")));
    private static final Set<String> TEXT_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "txt", "log", "gitignore", "gitattributes", "java", "js", "ts", "css", "scss", "html", "xml",
            "json", "yml", "yaml", "properties", "gradle", "py", "rb", "go", "rs", "sh", "bat", "sql",
            "c", "h", "cpp", "hpp", "cs", "kt", "swift", "php", "pl", "r", "scala", "clj", "hs", "lua",
            "vim", "conf", "cfg", "ini", "env", "dockerfile", "makefile", "cmake", "toml", "lock",
            "proto", "thrift", "graphql", "dart", "elm", "erlang", "ex", "exs", "fs", "fsx", "ml", "mli",
            "nim", "pas", "pp", "tcl", "vb", "vbs", "asm", "s", "m", "mm", "plist", "strings",
            "csv", "tsv", "psv", "dsv")));
    private static final Set<String> IMAGE_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "png", "jpg", "jpeg", "gif", "bmp", "svg", "webp", "avif")));
    private static final Set<String> PDF_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(
            Collections.singletonList("pdf")));
    private static final Set<String> WORD_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("doc", "docx")));
    private static final Set<String> EXCEL_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("xls", "xlsx")));
    private static final Set<String> POWERPOINT_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("ppt", "pptx")));

    private static final Map<String, String> HIGHLIGHT_LANGUAGE_MAP;

    static {
        Map<String, String> m = new HashMap<>();
        m.put("java", "java"); m.put("js", "javascript"); m.put("ts", "typescript");
        m.put("css", "css"); m.put("scss", "scss"); m.put("html", "html"); m.put("xml", "xml");
        m.put("json", "json"); m.put("yml", "yaml"); m.put("yaml", "yaml");
        m.put("sh", "bash"); m.put("bash", "bash"); m.put("bat", "dos");
        m.put("py", "python"); m.put("rb", "ruby"); m.put("go", "go"); m.put("rs", "rust");
        m.put("c", "c"); m.put("h", "c"); m.put("cpp", "cpp"); m.put("hpp", "cpp");
        m.put("cs", "csharp"); m.put("kt", "kotlin"); m.put("swift", "swift"); m.put("sql", "sql");
        HIGHLIGHT_LANGUAGE_MAP = Collections.unmodifiableMap(m);
    }

    private static final Parser MARKDOWN_PARSER = Parser.builder().build();
    private static final HtmlRenderer MARKDOWN_RENDERER = HtmlRenderer.builder().build();

    private final ShareLinkService shareLinkService;
    private final RepositoryService repositoryService;
    private final GitRepositoryService gitRepositoryService;

    public ShareController(ShareLinkService shareLinkService,
                           RepositoryService repositoryService,
                           GitRepositoryService gitRepositoryService) {
        this.shareLinkService = shareLinkService;
        this.repositoryService = repositoryService;
        this.gitRepositoryService = gitRepositoryService;
    }

    // ── Public share viewer ───────────────────────────────────────────────────

    /**
     * Show the public share viewer page. Handles password prompt and file preview.
     */
    @GetMapping("/share/{token}")
    public String showShare(@PathVariable String token,
                            @RequestParam(value = "pw", required = false) String pw,
                            Model model) {
        ShareLink link = shareLinkService.getByToken(token);
        if (link == null || !link.isActive()) {
            model.addAttribute("shareError", "share.invalid");
            return "share-viewer";
        }
        if (link.isExpired()) {
            model.addAttribute("shareError", "share.expired");
            return "share-viewer";
        }

        boolean hasPassword = link.getPasswordHash() != null;
        if (hasPassword) {
            if (pw == null || pw.isEmpty()) {
                // Show password prompt
                model.addAttribute("token", token);
                model.addAttribute("fileName", link.getFileName());
                model.addAttribute("requirePassword", true);
                return "share-viewer";
            }
            if (!shareLinkService.validateAccess(token, pw)) {
                model.addAttribute("token", token);
                model.addAttribute("fileName", link.getFileName());
                model.addAttribute("requirePassword", true);
                model.addAttribute("passwordWrong", true);
                return "share-viewer";
            }
        }

        // Build preview model
        try {
            File repoDir = repositoryService.getRepositoryPath(link.getRepoName());
            byte[] content = gitRepositoryService.getFileContent(repoDir, link.getBranch(), link.getFilePath());
            String detectedMime = detectMimeType(link.getFileName());
            String previewType = determinePreviewTypeWithContentDetection(link.getFileName(), detectedMime, content);
            String mimeType = guessMimeType(link.getFileName(), previewType, detectedMime);

            boolean tooLargeForInline = shouldRenderAsText(previewType) && content.length > MAX_INLINE_PREVIEW_BYTES;
            boolean binaryDetected = shouldRenderAsText(previewType) && isLikelyBinary(content);
            boolean inlinePreview = !tooLargeForInline && !binaryDetected && previewType != null && !"binary".equals(previewType);

            String pwParam = (hasPassword && pw != null && !pw.isEmpty())
                    ? "?pw=" + encodeUrlParam(pw) : "";

            model.addAttribute("token", token);
            model.addAttribute("fileName", link.getFileName());
            model.addAttribute("filePath", link.getFilePath());
            model.addAttribute("fileSizeFormatted", formatBytes(content.length));
            model.addAttribute("previewType", previewType);
            model.addAttribute("inlinePreview", inlinePreview);
            model.addAttribute("highlightLanguage", detectHighlightLanguage(link.getFileName()));
            model.addAttribute("tooLargeForInline", tooLargeForInline);
            model.addAttribute("binaryDetected", binaryDetected);
            model.addAttribute("rawUrl", "/share/" + token + "/raw" + pwParam);
            model.addAttribute("downloadUrl", "/share/" + token + "/download" + pwParam);
            model.addAttribute("clientRenderOffice", Arrays.asList("word", "excel", "powerpoint").contains(previewType));
            model.addAttribute("previewAvailable", inlinePreview || Arrays.asList("word", "excel", "powerpoint").contains(previewType));

            if (link.getExpiresAt() != null) {
                model.addAttribute("expiresAt",
                        link.getExpiresAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            }

            if (inlinePreview) {
                if ("markdown".equals(previewType)) {
                    Charset charset = detectCharset(content);
                    byte[] textBytes = stripBom(content, charset);
                    String markdown = new String(textBytes, charset);
                    Node document = MARKDOWN_PARSER.parse(markdown);
                    model.addAttribute("markdownHtml", MARKDOWN_RENDERER.render(document));
                    model.addAttribute("detectedCharset", charset.name());
                } else if ("text".equals(previewType)) {
                    Charset charset = detectCharset(content);
                    byte[] textBytes = stripBom(content, charset);
                    model.addAttribute("textContent", new String(textBytes, charset));
                    model.addAttribute("detectedCharset", charset.name());
                } else if ("image".equals(previewType)) {
                    model.addAttribute("imageData", "data:" + (mimeType != null ? mimeType : "image/*")
                            + ";base64," + Base64.getEncoder().encodeToString(content));
                } else if ("pdf".equals(previewType)) {
                    model.addAttribute("pdfData", "data:" + (mimeType != null ? mimeType : "application/pdf")
                            + ";base64," + Base64.getEncoder().encodeToString(content));
                }
            }
        } catch (Exception e) {
            logger.error("Error loading shared file for token {}", token, e);
            model.addAttribute("shareError", "share.file.error");
        }
        return "share-viewer";
    }

    /**
     * Serve raw file content for a share link (inline, used by Office renderers).
     */
    @GetMapping("/share/{token}/raw")
    public ResponseEntity<byte[]> shareRaw(@PathVariable String token,
                                           @RequestParam(value = "pw", required = false) String pw) {
        return serveSharedFile(token, pw, false);
    }

    /**
     * Serve file as a download attachment for a share link.
     */
    @GetMapping("/share/{token}/download")
    public ResponseEntity<byte[]> shareDownload(@PathVariable String token,
                                                @RequestParam(value = "pw", required = false) String pw) {
        return serveSharedFile(token, pw, true);
    }

    private ResponseEntity<byte[]> serveSharedFile(String token, String pw, boolean attachment) {
        ShareLink link = shareLinkService.getByToken(token);
        if (link == null || !link.isAccessible()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (link.getPasswordHash() != null && !shareLinkService.validateAccess(token, pw)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            File repoDir = repositoryService.getRepositoryPath(link.getRepoName());
            byte[] content = gitRepositoryService.getFileContent(repoDir, link.getBranch(), link.getFilePath());
            String detectedMime = detectMimeType(link.getFileName());
            String previewType = determinePreviewType(link.getFileName(), detectedMime);
            String mimeType = guessMimeType(link.getFileName(), previewType, detectedMime);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mimeType != null ? MediaType.parseMediaType(mimeType) : MediaType.APPLICATION_OCTET_STREAM);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(attachment, link.getFileName()));
            headers.setContentLength(content.length);
            return new ResponseEntity<>(content, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error serving shared file for token {}", token, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ── Authenticated admin endpoints ─────────────────────────────────────────

    /**
     * Create a new share link. Returns JSON.
     */
    @PostMapping("/admin/share/create")
    @ResponseBody
    public Map<String, Object> createShare(
            @RequestParam String repoName,
            @RequestParam String filePath,
            @RequestParam(required = false) String branch,
            @RequestParam(defaultValue = "0") long expiresInHours,
            @RequestParam(required = false) String password,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String normalizedName = repositoryService.normalizeRepositoryName(repoName);
            if (!repositoryService.repositoryExists(normalizedName)) {
                result.put("error", "Repository not found");
                return result;
            }
            String fileName = filePath.contains("/")
                    ? filePath.substring(filePath.lastIndexOf('/') + 1) : filePath;
            ShareLink link = shareLinkService.createShareLink(
                    normalizedName, filePath, branch, fileName, expiresInHours, password);
            String base = getBaseUrl(request);
            result.put("token", link.getToken());
            result.put("shareUrl", base + "/share/" + link.getToken());
            result.put("hasPassword", link.getPasswordHash() != null);
            result.put("expiresAt", link.getExpiresAt() != null
                    ? link.getExpiresAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : null);
        } catch (Exception e) {
            logger.error("Error creating share link for repo={} path={}", repoName, filePath, e);
            result.put("error", "Failed to create share link");
        }
        return result;
    }

    /**
     * Revoke an existing share link. Returns JSON.
     */
    @PostMapping("/admin/share/{token}/revoke")
    @ResponseBody
    public Map<String, Object> revokeShare(@PathVariable String token) {
        Map<String, Object> result = new HashMap<>();
        shareLinkService.revokeToken(token);
        result.put("ok", true);
        return result;
    }

    /**
     * List all share links. Returns JSON.
     */
    @GetMapping("/admin/share/list")
    @ResponseBody
    public Object listShares() {
        return shareLinkService.listAll();
    }

    // ── File preview helper methods ───────────────────────────────────────────

    private String determinePreviewType(String fileName, String mimeType) {
        String ext = extractExtension(fileName);
        if (ext != null) {
            if (MARKDOWN_EXTENSIONS.contains(ext)) return "markdown";
            if (TEXT_EXTENSIONS.contains(ext)) return "text";
            if (IMAGE_EXTENSIONS.contains(ext)) return "image";
            if (PDF_EXTENSIONS.contains(ext)) return "pdf";
            if (WORD_EXTENSIONS.contains(ext)) return "word";
            if (EXCEL_EXTENSIONS.contains(ext)) return "excel";
            if (POWERPOINT_EXTENSIONS.contains(ext)) return "powerpoint";
        }
        if (mimeType != null) {
            if (mimeType.startsWith("text/")) return "text";
            if (mimeType.startsWith("image/")) return "image";
            if ("application/pdf".equals(mimeType)) return "pdf";
        }
        return "binary";
    }

    private String determinePreviewTypeWithContentDetection(String fileName, String mimeType, byte[] content) {
        String previewType = determinePreviewType(fileName, mimeType);
        if ("binary".equals(previewType) && content != null && content.length > 0 && isLikelyTextContent(content)) {
            return "text";
        }
        return previewType;
    }

    private boolean isLikelyTextContent(byte[] content) {
        if (content == null || content.length == 0) return false;
        int checkLength = Math.min(content.length, 8192);
        int controlChars = 0, printableChars = 0, nullBytes = 0;
        for (int i = 0; i < checkLength; i++) {
            int b = content[i] & 0xFF;
            if (b == 0) { if (++nullBytes > 3) return false; }
            if ((b >= 0x20 && b <= 0x7E) || b == 0x09 || b == 0x0A || b == 0x0D || (b >= 0x80))
                printableChars++;
            else if (b < 0x20) controlChars++;
        }
        return (double) printableChars / checkLength > 0.7
                && (double) controlChars / checkLength < 0.1
                && nullBytes <= 2;
    }

    private boolean isLikelyBinary(byte[] content) {
        int length = Math.min(content.length, 4096);
        int controlChars = 0;
        for (int i = 0; i < length; i++) {
            int b = content[i] & 0xFF;
            if (b == 0) return true;
            if (b < 0x09 || (b > 0x0D && b < 0x20)) controlChars++;
        }
        return controlChars > length / 8;
    }

    private boolean shouldRenderAsText(String previewType) {
        return "text".equals(previewType) || "markdown".equals(previewType);
    }

    private String detectHighlightLanguage(String fileName) {
        String ext = extractExtension(fileName);
        return ext == null ? null : HIGHLIGHT_LANGUAGE_MAP.get(ext);
    }

    private String detectMimeType(String fileName) {
        if (fileName == null) return null;
        try { return URLConnection.getFileNameMap().getContentTypeFor(fileName); } catch (Exception e) { return null; }
    }

    private String guessMimeType(String fileName, String previewType, String detectedMime) {
        String ext = extractExtension(fileName);
        if ("markdown".equals(previewType)) return "text/markdown;charset=UTF-8";
        if ("text".equals(previewType)) return "text/plain;charset=UTF-8";
        if ("image".equals(previewType)) {
            if ("svg".equals(ext)) return "image/svg+xml";
            if (ext != null) return "image/" + ext.replace("jpg", "jpeg");
        }
        if ("pdf".equals(previewType)) return "application/pdf";
        if ("word".equals(previewType)) return "docx".equals(ext)
                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document" : "application/msword";
        if ("excel".equals(previewType)) {
            if ("xlsx".equals(ext)) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            if ("csv".equals(ext)) return "text/csv;charset=UTF-8";
            return "application/vnd.ms-excel";
        }
        if ("powerpoint".equals(previewType)) return "pptx".equals(ext)
                ? "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                : "application/vnd.ms-powerpoint";
        return detectedMime != null ? detectedMime : detectMimeType(fileName);
    }

    private String buildContentDisposition(boolean attachment, String fileName) {
        String type = attachment ? "attachment" : "inline";
        String escaped = fileName.replace("\"", "\\\"");
        return type + "; filename=\"" + escaped + "\"; filename*=UTF-8''" + encodeUrlParam(fileName);
    }

    private Charset detectCharset(byte[] content) {
        if (content == null || content.length == 0) return StandardCharsets.UTF_8;
        if (content.length >= 3 && (content[0] & 0xFF) == 0xEF && (content[1] & 0xFF) == 0xBB && (content[2] & 0xFF) == 0xBF)
            return StandardCharsets.UTF_8;
        if (content.length >= 2 && (content[0] & 0xFF) == 0xFE && (content[1] & 0xFF) == 0xFF)
            return StandardCharsets.UTF_16BE;
        if (content.length >= 2 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xFE)
            return StandardCharsets.UTF_16LE;
        if (isValidUtf8Bytes(content)) return StandardCharsets.UTF_8;
        try { return Charset.forName("GBK"); } catch (Exception e) { return StandardCharsets.ISO_8859_1; }
    }

    private byte[] stripBom(byte[] content, Charset charset) {
        if (content == null || content.length == 0) return content;
        if (charset == StandardCharsets.UTF_8 && content.length >= 3
                && (content[0] & 0xFF) == 0xEF && (content[1] & 0xFF) == 0xBB && (content[2] & 0xFF) == 0xBF)
            return Arrays.copyOfRange(content, 3, content.length);
        if ((charset == StandardCharsets.UTF_16BE || charset == StandardCharsets.UTF_16LE) && content.length >= 2)
            return Arrays.copyOfRange(content, 2, content.length);
        return content;
    }

    private boolean isValidUtf8Bytes(byte[] bytes) {
        int length = Math.min(bytes.length, UTF8_VALIDATION_BUFFER_SIZE);
        int i = 0;
        while (i < length) {
            int b = bytes[i] & 0xFF;
            int seqLen;
            if (b < 0x80) seqLen = 1;
            else if ((b & 0xE0) == 0xC0 && b >= 0xC2) seqLen = 2;
            else if ((b & 0xF0) == 0xE0) seqLen = 3;
            else if ((b & 0xF8) == 0xF0 && b <= 0xF4) seqLen = 4;
            else return false;
            for (int j = 1; j < seqLen; j++) {
                if (i + j >= length) return false;
                if ((bytes[i + j] & 0xC0) != 0x80) return false;
            }
            i += seqLen;
        }
        return true;
    }

    private String extractExtension(String fileName) {
        if (fileName == null) return null;
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return null;
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String encodeUrlParam(String value) {
        if (value == null) return "";
        try { return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20"); }
        catch (Exception e) { return value; }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isEmpty()) scheme = request.getScheme();
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isEmpty()) {
            host = request.getHeader("Host");
            if (host == null || host.isEmpty()) {
                host = request.getServerName();
                int port = request.getServerPort();
                if (port > 0 && !((port == 80 && "http".equals(scheme)) || (port == 443 && "https".equals(scheme))))
                    host = host + ":" + port;
            }
        }
        // Strip comma-separated values in forwarded headers
        int comma = host.indexOf(',');
        if (comma > 0) host = host.substring(0, comma).trim();
        return scheme + "://" + host;
    }
}
