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
 * Repository management REST controller.
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
     * Create repository.
     */
    @PostMapping
    public ResponseEntity<?> createRepository(@RequestParam String name) {
        try {
            logger.info("Creating repository: {}", name);
            
            // Validate repository name.
            if (name == null || name.trim().isEmpty()) {
                return createErrorResponse("INVALID_NAME", "validation.name.required", HttpStatus.BAD_REQUEST);
            }
            
            if (!repositoryService.isValidRepositoryName(name)) {
                return createErrorResponse("INVALID_NAME", "validation.name.invalid", HttpStatus.BAD_REQUEST);
            }
            
            // Check if repository already exists.
            String normalizedName = repositoryService.normalizeRepositoryName(name);
            if (repositoryService.repositoryExists(normalizedName)) {
                return createErrorResponse("REPO_ALREADY_EXISTS", "repo.exists", HttpStatus.BAD_REQUEST, normalizedName);
            }
            
            // Create repository.
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
     * List repositories.
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
     * Create error response.
     */
    private ResponseEntity<ErrorResponse> createErrorResponse(String errorCode, String messageKey, HttpStatus status, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        String message = messageSource.getMessage(messageKey, args, messageKey, locale);
        
        ErrorResponse errorResponse = new ErrorResponse(errorCode, message);
        return ResponseEntity.status(status).body(errorResponse);
    }
}
