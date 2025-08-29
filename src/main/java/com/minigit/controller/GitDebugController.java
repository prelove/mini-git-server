package com.minigit.controller;

import com.minigit.service.GitRepositoryService;
import com.minigit.service.RepositoryService;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Git调试控制器
 */
@RestController
@RequestMapping("/debug/git")
public class GitDebugController {

    private final RepositoryService repositoryService;
    private final GitRepositoryService gitRepositoryService;

    public GitDebugController(RepositoryService repositoryService, GitRepositoryService gitRepositoryService) {
        this.repositoryService = repositoryService;
        this.gitRepositoryService = gitRepositoryService;
    }

    /**
     * 调试仓库状态
     */
    @GetMapping("/repo/{name}")
    public Map<String, Object> debugRepository(@PathVariable String name) {
        Map<String, Object> debug = new HashMap<>();
        
        try {
            String normalizedName = repositoryService.normalizeRepositoryName(name);
            File repoDir = repositoryService.getRepositoryPath(normalizedName);
            
            debug.put("repoName", normalizedName);
            debug.put("repoPath", repoDir.getAbsolutePath());
            debug.put("repoExists", repoDir.exists());
            debug.put("isDirectory", repoDir.isDirectory());
            
            // 列出仓库目录内容
            if (repoDir.exists()) {
                File[] files = repoDir.listFiles();
                if (files != null) {
                    Map<String, Object> dirContents = new HashMap<>();
                    for (File file : files) {
                        dirContents.put(file.getName(), file.isDirectory() ? "DIR" : "FILE(" + file.length() + ")");
                    }
                    debug.put("directoryContents", dirContents);
                }
            }
            
            // 尝试打开Git仓库
            try (Repository repository = new FileRepositoryBuilder()
                    .setGitDir(repoDir)
                    .setMustExist(true)
                    .build()) {
                
                debug.put("gitRepoValid", true);
                debug.put("gitDirPath", repository.getDirectory().getAbsolutePath());
                
                try (Git git = new Git(repository)) {
                    // 获取所有引用
                    List<Ref> refs = git.branchList().call();
                    Map<String, String> branches = new HashMap<>();
                    for (Ref ref : refs) {
                        branches.put(ref.getName(), ref.getObjectId() != null ? ref.getObjectId().getName() : "null");
                    }
                    debug.put("branches", branches);
                    
                    // 获取HEAD引用
                    Ref head = repository.exactRef("HEAD");
                    if (head != null) {
                        debug.put("HEAD", head.getName());
                        debug.put("HEADTarget", head.getTarget() != null ? head.getTarget().getName() : "null");
                        debug.put("HEADObjectId", head.getObjectId() != null ? head.getObjectId().getName() : "null");
                    }
                    
                    // 尝试获取提交
                    try {
                        List<GitRepositoryService.CommitInfo> commits = gitRepositoryService.getCommitLog(repoDir, 5);
                        debug.put("commitCount", commits.size());
                        debug.put("commits", commits);
                    } catch (Exception e) {
                        debug.put("commitError", e.getMessage());
                    }
                    
                    // 尝试获取分支
                    try {
                        List<GitRepositoryService.BranchInfo> branchInfos = gitRepositoryService.getBranches(repoDir);
                        debug.put("branchInfoCount", branchInfos.size());
                        debug.put("branchInfos", branchInfos);
                    } catch (Exception e) {
                        debug.put("branchError", e.getMessage());
                    }
                }
                
            } catch (Exception e) {
                debug.put("gitRepoValid", false);
                debug.put("gitError", e.getMessage());
                debug.put("gitErrorClass", e.getClass().getSimpleName());
            }
            
        } catch (Exception e) {
            debug.put("error", e.getMessage());
            debug.put("errorClass", e.getClass().getSimpleName());
        }
        
        return debug;
    }
}