package com.minigit.controller;

import com.minigit.dto.ErrorResponse;
import com.minigit.dto.RepositoryResponse;
import com.minigit.service.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * 仓库管理REST控制器
 */
@RestController
@RequestMapping("/api/repos")
public class RepositoryController {

    private static final Logger logger = LoggerFactory.getLogger(RepositoryController.class);

    private final RepositoryService repositoryService;
    private final MessageSource messageSource;

    public RepositoryController(RepositoryService repositoryService, MessageSource messageSource) {
        this.repositoryService = repositoryService;
        this.messageSource = messageSource;
    }

    /**
     * 创建仓库
     */
    @PostMapping
    public ResponseEntity<?> createRepository(@RequestParam String name) {
        try {
            logger.info("Creating repository: {}", name);
            
            // 验证仓库名称
            if (name == null || name.trim().isEmpty()) {
                return createErrorResponse("INVALID_NAME", "validation.name.required", HttpStatus.BAD_REQUEST);
            }
            
            if (!repositoryService.isValidRepositoryName(name)) {
                return createErrorResponse("INVALID_NAME", "validation.name.invalid", HttpStatus.BAD_REQUEST);
            }
            
            // 检查仓库是否已存在
            String normalizedName = repositoryService.normalizeRepositoryName(name);
            if (repositoryService.repositoryExists(normalizedName)) {
                return createErrorResponse("REPO_ALREADY_EXISTS", "repo.exists", HttpStatus.BAD_REQUEST, normalizedName);
            }
            
            // 创建仓库
            File repoDir = repositoryService.createRepository(name);
            
            logger.info("Repository created successfully: {}", normalizedName);
            return ResponseEntity.ok(new RepositoryResponse(normalizedName));
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid repository creation request: {}", e.getMessage());
            return createErrorResponse("INVALID_REQUEST", "repo.invalid", HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            logger.error("Failed to create repository: {}", name, e);
            return createErrorResponse("INTERNAL_ERROR", "internal.error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 列出所有仓库
     */
    @GetMapping
    public ResponseEntity<List<String>> listRepositories() {
        try {
            logger.debug("Listing repositories");
            List<String> repositories = repositoryService.listRepositories();
            return ResponseEntity.ok(repositories);
        } catch (Exception e) {
            logger.error("Failed to list repositories", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 创建错误响应
     */
    private ResponseEntity<ErrorResponse> createErrorResponse(String errorCode, String messageKey, HttpStatus status, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        String message = messageSource.getMessage(messageKey, args, messageKey, locale);
        
        ErrorResponse errorResponse = new ErrorResponse(errorCode, message);
        return ResponseEntity.status(status).body(errorResponse);
    }
}