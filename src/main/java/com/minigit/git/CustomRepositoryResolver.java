package com.minigit.git;

import com.minigit.service.RepositoryService;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.security.Principal;

/**
 * Custom repository resolver (updated version).
 * Resolves Git HTTP request paths to actual repository locations.
 */
@Component
public class CustomRepositoryResolver implements RepositoryResolver<HttpServletRequest> {

    private static final Logger logger = LoggerFactory.getLogger(CustomRepositoryResolver.class);

    private final RepositoryService repositoryService;
    private final GitAccessLogger gitAccessLogger;

    public CustomRepositoryResolver(RepositoryService repositoryService, GitAccessLogger gitAccessLogger) {
        this.repositoryService = repositoryService;
        this.gitAccessLogger = gitAccessLogger;
    }

    @Override
    public Repository open(HttpServletRequest request, String name) 
            throws RepositoryNotFoundException, ServiceNotAuthorizedException, 
                   ServiceNotEnabledException, ServiceMayNotContinueException {
        
        long startTime = System.currentTimeMillis();
        String operation = determineOperation(request);
        String user = getUserName(request);
        boolean success = false;
        
        try {
            logger.info("Opening repository: {} for operation: {} by user: {}", name, operation, user);
            
            // Normalize repository name.
            String normalizedName = repositoryService.normalizeRepositoryName(name);
            
            // Check whether the repository exists.
            if (!repositoryService.repositoryExists(normalizedName)) {
                logger.warn("Repository not found: {}", normalizedName);
                throw new RepositoryNotFoundException(normalizedName);
            }
            
            // Resolve repository path.
            File repoDir = repositoryService.getRepositoryPath(normalizedName);
            
            // Build and open the repository.
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repository = builder
                .setGitDir(repoDir)
                .setMustExist(true)
                .build();
            
            success = true;
            logger.debug("Successfully opened repository: {}", repoDir.getAbsolutePath());
            return repository;
            
        } catch (IOException e) {
            logger.error("Failed to open repository: {}", name, e);
            throw new RepositoryNotFoundException(name, e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            gitAccessLogger.logGitOperation(request, name, operation, user, success, duration);
        }
    }

    /**
     * Determine Git operation type.
     */
    private String determineOperation(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        
        if (queryString != null) {
            if (queryString.contains("git-upload-pack")) {
                return "FETCH/CLONE";
            } else if (queryString.contains("git-receive-pack")) {
                return "PUSH";
            }
        }
        
        if (uri.contains("git-upload-pack")) {
            return "FETCH/CLONE";
        } else if (uri.contains("git-receive-pack")) {
            return "PUSH";
        }
        
        return "INFO_REFS";
    }

    /**
     * Get the username.
     */
    private String getUserName(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal != null) {
            return principal.getName();
        }
        return "anonymous";
    }
}
