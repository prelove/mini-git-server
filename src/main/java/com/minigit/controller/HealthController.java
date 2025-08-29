package com.minigit.controller;

import com.minigit.service.RepositoryService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 健康检查控制器
 */
@Controller
@RequestMapping("/actuator")
public class HealthController {

    private final VcsHealthIndicator vcsHealthIndicator;

    public HealthController(VcsHealthIndicator vcsHealthIndicator) {
        this.vcsHealthIndicator = vcsHealthIndicator;
    }

    /**
     * 健康检查 - 根据Accept头决定返回HTML还是JSON
     */
    @GetMapping("/health")
    public String healthPage(
            HttpServletRequest request, 
            @RequestParam(value = "format", required = false) String format,
            Model model) {
        
        // 如果明确要求JSON格式，或者Accept头包含application/json但不包含text/html
        String acceptHeader = request.getHeader("Accept");
        boolean preferJson = "json".equals(format) || 
                             (acceptHeader != null && 
                              acceptHeader.contains("application/json") && 
                              !acceptHeader.contains("text/html"));
        
        if (preferJson) {
            // 重定向到JSON端点
            return "redirect:/actuator/health/json";
        }
        
        // 返回HTML页面
        Health health = vcsHealthIndicator.health();
        
        // 获取系统健康状态
        String overallStatus = health.getStatus().getCode();
        Map<String, Object> details = health.getDetails();
        
        // 解析详细信息
        Map<String, Object> vcsDetails = (Map<String, Object>) details.get("vcs");
        Map<String, Object> diskDetails = (Map<String, Object>) details.get("diskSpace");
        Map<String, Object> pingDetails = (Map<String, Object>) details.get("ping");
        
        model.addAttribute("overallStatus", overallStatus);
        model.addAttribute("checkTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        
        // VCS信息
        if (vcsDetails != null) {
            model.addAttribute("vcsStatus", "UP");
            model.addAttribute("storageDir", vcsDetails.get("storage"));
            model.addAttribute("repositories", vcsDetails.get("repositories"));
            model.addAttribute("version", vcsDetails.get("version"));
        } else {
            model.addAttribute("vcsStatus", "DOWN");
        }
        
        // 磁盘信息
        if (diskDetails != null && diskDetails.get("details") != null) {
            Map<String, Object> diskDetailInfo = (Map<String, Object>) diskDetails.get("details");
            long totalSpace = ((Number) diskDetailInfo.get("total")).longValue();
            long freeSpace = ((Number) diskDetailInfo.get("free")).longValue();
            long usedSpace = totalSpace - freeSpace;
            
            model.addAttribute("diskStatus", diskDetails.get("status"));
            model.addAttribute("totalSpace", formatBytes(totalSpace));
            model.addAttribute("freeSpace", formatBytes(freeSpace));
            model.addAttribute("usedSpace", formatBytes(usedSpace));
            model.addAttribute("usagePercent", Math.round((double) usedSpace / totalSpace * 100));
        }
        
        // 网络状态
        if (pingDetails != null) {
            model.addAttribute("pingStatus", pingDetails.get("status"));
        }
        
        return "health";
    }

    /**
     * JSON格式的健康检查（明确的JSON端点）
     */
    @GetMapping(value = "/health/json", produces = "application/json")
    @ResponseBody
    public Map<String, Object> healthJson() {
        Health health = vcsHealthIndicator.health();
        
        Map<String, Object> result = new HashMap<>();
        result.put("status", health.getStatus().getCode());
        result.put("details", health.getDetails());
        result.put("timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date()));
        
        return result;
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

/**
 * VCS健康指示器
 */
@Component
class VcsHealthIndicator implements HealthIndicator {

    private final RepositoryService repositoryService;

    public VcsHealthIndicator(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    public Health health() {
        try {
            // 检查存储目录是否可访问
            File storageDir = ((com.minigit.service.impl.RepositoryServiceImpl) repositoryService).getStorageDir();
            
            if (!storageDir.exists() || !storageDir.canRead() || !storageDir.canWrite()) {
                return Health.down()
                    .withDetail("storage", "Storage directory is not accessible: " + storageDir.getAbsolutePath())
                    .build();
            }

            // 统计仓库数量
            int repoCount = repositoryService.listRepositories().size();
            
            return Health.up()
                .withDetail("storage", storageDir.getAbsolutePath())
                .withDetail("repositories", repoCount)
                .withDetail("version", "1.0.0")
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}