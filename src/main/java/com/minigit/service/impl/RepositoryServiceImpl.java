package com.minigit.service.impl;

import com.minigit.config.VcsProperties;
import com.minigit.service.RepositoryService;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 仓库服务实现
 */
@Service
public class RepositoryServiceImpl implements RepositoryService {

    private static final Logger logger = LoggerFactory.getLogger(RepositoryServiceImpl.class);

    private final VcsProperties vcsProperties;
    private File storageDir;

    // 仓库名称验证正则表达式：只允许字母、数字、下划线、中划线
    private static final Pattern REPO_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    public RepositoryServiceImpl(VcsProperties vcsProperties) {
        this.vcsProperties = vcsProperties;
    }

    @PostConstruct
    public void init() {
        String storagePath = vcsProperties.getStorage().getDir();
        this.storageDir = new File(storagePath);
        
        // 确保存储目录存在
        if (!storageDir.exists()) {
            boolean created = storageDir.mkdirs();
            if (created) {
                logger.info("Created storage directory: {}", storageDir.getAbsolutePath());
            } else {
                logger.error("Failed to create storage directory: {}", storageDir.getAbsolutePath());
                throw new RuntimeException("Cannot create storage directory");
            }
        }
        
        logger.info("Repository storage directory: {}", storageDir.getAbsolutePath());
    }

    @Override
    public File createRepository(String name) {
        String normalizedName = normalizeRepositoryName(name);
        
        if (!isValidRepositoryName(name)) {
            throw new IllegalArgumentException("Invalid repository name: " + name);
        }

        File repoDir = new File(storageDir, normalizedName);
        
        if (repoDir.exists()) {
            throw new IllegalArgumentException("Repository already exists: " + normalizedName);
        }

        try {
            // 使用JGit创建裸仓库
            InitCommand initCommand = Git.init();
            initCommand.setDirectory(repoDir);
            initCommand.setBare(true); // 创建裸仓库
            
            Git git = initCommand.call();
            git.close();
            
            logger.info("Created bare repository: {}", repoDir.getAbsolutePath());
            return repoDir;
            
        } catch (Exception e) {
            logger.error("Failed to create repository: {}", normalizedName, e);
            throw new RuntimeException("Failed to create repository", e);
        }
    }

    @Override
    public List<String> listRepositories() {
        List<String> repositories = new ArrayList<>();
        
        if (!storageDir.exists() || !storageDir.isDirectory()) {
            return repositories;
        }

        File[] files = storageDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && file.getName().endsWith(".git")) {
                    repositories.add(file.getName());
                }
            }
        }
        
        logger.debug("Found {} repositories", repositories.size());
        return repositories;
    }

    @Override
    public boolean repositoryExists(String name) {
        String normalizedName = normalizeRepositoryName(name);
        File repoDir = new File(storageDir, normalizedName);
        return repoDir.exists() && repoDir.isDirectory();
    }

    @Override
    public File getRepositoryPath(String name) {
        String normalizedName = normalizeRepositoryName(name);
        return new File(storageDir, normalizedName);
    }

    @Override
    public boolean isValidRepositoryName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        // 移除.git后缀进行验证
        String nameToValidate = name;
        if (name.endsWith(".git")) {
            nameToValidate = name.substring(0, name.length() - 4);
        }
        
        // 检查是否包含危险字符
        if (nameToValidate.contains("..") || nameToValidate.contains("/") || nameToValidate.contains("\\")) {
            return false;
        }
        
        // 检查是否符合命名规范
        return REPO_NAME_PATTERN.matcher(nameToValidate).matches();
    }

    @Override
    public String normalizeRepositoryName(String name) {
        if (name == null) {
            return null;
        }
        
        String normalized = name.trim();
        
        // 如果没有.git后缀，自动添加
        if (!normalized.endsWith(".git")) {
            normalized += ".git";
        }
        
        return normalized;
    }

    /**
     * 获取存储目录
     */
    public File getStorageDir() {
        return storageDir;
    }
}