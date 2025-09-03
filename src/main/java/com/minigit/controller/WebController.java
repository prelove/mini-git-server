package com.minigit.controller;

import com.minigit.service.GitRepositoryService;
import com.minigit.service.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Web管理界面控制器
 */
@Controller
@RequestMapping("/")
public class WebController {

    private static final Logger logger = LoggerFactory.getLogger(WebController.class);

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
                            Model model) {
        
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
            model.addAttribute("cloneUrl", getCloneUrl(normalizedName));
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
     * 获取克隆URL
     */
    private String getCloneUrl(String repoName) {
        // 这里简化处理，实际应该从请求中获取主机和端口
        return String.format("http://localhost:8080/git/%s", repoName);
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
}