package com.minigit.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Git仓库操作服务
 */
@Service
public class GitRepositoryService {

    private static final Logger logger = LoggerFactory.getLogger(GitRepositoryService.class);

    /**
     * 提交信息数据结构
     */
    public static class CommitInfo {
        private String id;
        private String shortId;
        private String message;
        private String author;
        private String email;
        private Date date;
        private String dateFormatted;

        // getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getShortId() { return shortId; }
        public void setShortId(String shortId) { this.shortId = shortId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
        public String getDateFormatted() { return dateFormatted; }
        public void setDateFormatted(String dateFormatted) { this.dateFormatted = dateFormatted; }
    }

    /**
     * 文件信息数据结构
     */
    public static class FileInfo {
        private String name;
        private String path;
        private String type; // "file" or "directory"
        private long size;
        private String sizeFormatted;

        // getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
        public String getSizeFormatted() { return sizeFormatted; }
        public void setSizeFormatted(String sizeFormatted) { this.sizeFormatted = sizeFormatted; }
    }

    /**
     * 分支信息数据结构
     */
    public static class BranchInfo {
        private String name;
        private String shortName;
        private boolean isDefault;
        private String lastCommitId;
        private String lastCommitMessage;
        private Date lastCommitDate;

        // getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getShortName() { return shortName; }
        public void setShortName(String shortName) { this.shortName = shortName; }
        public boolean isDefault() { return isDefault; }
        public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
        public String getLastCommitId() { return lastCommitId; }
        public void setLastCommitId(String lastCommitId) { this.lastCommitId = lastCommitId; }
        public String getLastCommitMessage() { return lastCommitMessage; }
        public void setLastCommitMessage(String lastCommitMessage) { this.lastCommitMessage = lastCommitMessage; }
        public Date getLastCommitDate() { return lastCommitDate; }
        public void setLastCommitDate(Date lastCommitDate) { this.lastCommitDate = lastCommitDate; }
    }

    /**
     * 获取仓库的提交日志
     */
    public List<CommitInfo> getCommitLog(File repoDir, int maxCount) throws Exception {
        List<CommitInfo> commits = new ArrayList<>();
        
        logger.debug("Getting commit log for repository: {}", repoDir.getAbsolutePath());
        
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(repoDir)
                .setMustExist(true)
                .build()) {
            
            try (Git git = new Git(repository)) {
                // 首先尝试获取HEAD引用
                Ref head = repository.exactRef("HEAD");
                if (head == null || head.getObjectId() == null) {
                    logger.warn("No HEAD reference found in repository: {}", repoDir.getAbsolutePath());
                    return commits;
                }
                
                LogCommand logCommand = git.log()
                    .add(head.getObjectId())
                    .setMaxCount(maxCount);
                
                for (RevCommit commit : logCommand.call()) {
                    CommitInfo info = new CommitInfo();
                    info.setId(commit.getId().getName());
                    info.setShortId(commit.getId().abbreviate(8).name());
                    info.setMessage(commit.getShortMessage());
                    info.setAuthor(commit.getAuthorIdent().getName());
                    info.setEmail(commit.getAuthorIdent().getEmailAddress());
                    info.setDate(commit.getAuthorIdent().getWhen());
                    info.setDateFormatted(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(info.getDate()));
                    
                    commits.add(info);
                }
                
                logger.debug("Found {} commits in repository", commits.size());
            }
        }
        
        return commits;
    }
    
    /**
     * 获取指定分支的提交日志（branchName 可以是短名 "master" 或完整 refs/heads/...）
     */
    public List<CommitInfo> getCommitLog(File repoDir, String branchName, int maxCount) throws Exception {
        List<CommitInfo> commits = new ArrayList<>();

        logger.debug("Getting commit log for repository: {}, branch: {}, maxCount: {}", repoDir.getAbsolutePath(), branchName, maxCount);

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(repoDir)
                .setMustExist(true)
                .build()) {

            try (Git git = new Git(repository)) {
                ObjectId startId = null;

                // 如果没有指定分支，则使用 HEAD
                if (branchName == null || branchName.isEmpty()) {
                    Ref head = repository.exactRef("HEAD");
                    if (head == null) {
                        logger.warn("No HEAD found for repository {}", repoDir.getAbsolutePath());
                        return commits;
                    }
                    startId = head.getObjectId();
                } else {
                    // 规范化分支名
                    String resolvedBranch = branchName;
                    if (!resolvedBranch.startsWith("refs/")) {
                        // 假定为本地分支短名
                        resolvedBranch = "refs/heads/" + resolvedBranch;
                    }

                    // 尝试解析引用到对象ID
                    startId = repository.resolve(resolvedBranch);
                    if (startId == null) {
                        logger.warn("Branch {} could not be resolved in repository {}", resolvedBranch, repoDir.getAbsolutePath());
                        // 作为退路尝试 HEAD
                        Ref head = repository.exactRef("HEAD");
                        if (head != null) {
                            startId = head.getObjectId();
                        } else {
                            return commits;
                        }
                    }
                }

                if (startId == null) {
                    logger.warn("No start commit found for repository {}", repoDir.getAbsolutePath());
                    return commits;
                }

                LogCommand logCommand = git.log()
                        .add(startId)
                        .setMaxCount(maxCount);

                for (RevCommit commit : logCommand.call()) {
                    CommitInfo info = new CommitInfo();
                    info.setId(commit.getId().getName());
                    info.setShortId(commit.getId().abbreviate(8).name());
                    info.setMessage(commit.getShortMessage());
                    info.setAuthor(commit.getAuthorIdent().getName());
                    info.setEmail(commit.getAuthorIdent().getEmailAddress());
                    info.setDate(commit.getAuthorIdent().getWhen());
                    info.setDateFormatted(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(info.getDate()));

                    commits.add(info);
                }

                logger.debug("Found {} commits for branch {}", commits.size(), branchName == null ? "HEAD" : branchName);
            }
        }

        return commits;
    }

    /**
     * 获取仓库的分支列表
     */
    public List<BranchInfo> getBranches(File repoDir) throws Exception {
        List<BranchInfo> branches = new ArrayList<>();
        
        logger.debug("Getting branches for repository: {}", repoDir.getAbsolutePath());
        
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(repoDir)
                .setMustExist(true)
                .build()) {
            
            try (Git git = new Git(repository)) {
                // 获取所有本地分支
                List<Ref> localRefs = git.branchList().call();
                logger.debug("Found {} local branch references", localRefs.size());
                
                // 获取所有远程分支
                List<Ref> remoteRefs = git.branchList().setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE).call();
                logger.debug("Found {} remote branch references", remoteRefs.size());
                
                // 合并所有分支引用
                List<Ref> allRefs = new ArrayList<>();
                allRefs.addAll(localRefs);
                allRefs.addAll(remoteRefs);
                
                String defaultBranch = getDefaultBranch(repository);
                logger.debug("Default branch: {}", defaultBranch);
                
                for (Ref ref : allRefs) {
                    logger.debug("Processing ref: {} -> {}", ref.getName(), 
                        ref.getObjectId() != null ? ref.getObjectId().getName() : "null");
                    
                    // 只处理本地分支 (refs/heads/*)
                    if (!ref.getName().startsWith("refs/heads/")) {
                        continue;
                    }
                    
                    BranchInfo info = new BranchInfo();
                    info.setName(ref.getName());
                    info.setShortName(Repository.shortenRefName(ref.getName()));
                    info.setDefault(ref.getName().equals(defaultBranch));
                    
                    // 获取最后一次提交信息
                    ObjectId objectId = ref.getObjectId();
                    if (objectId != null) {
                        try {
                            Iterable<RevCommit> commits = git.log().add(objectId).setMaxCount(1).call();
                            for (RevCommit commit : commits) {
                                info.setLastCommitId(commit.getId().abbreviate(8).name());
                                info.setLastCommitMessage(commit.getShortMessage());
                                info.setLastCommitDate(commit.getAuthorIdent().getWhen());
                                logger.debug("Branch {} last commit: {} - {}", 
                                    info.getShortName(), info.getLastCommitId(), info.getLastCommitMessage());
                                break;
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to get last commit for branch {}: {}", ref.getName(), e.getMessage());
                        }
                    }
                    
                    branches.add(info);
                }
                
                logger.debug("Returning {} branches", branches.size());
            }
        }
        
        return branches;
    }

    /**
     * 获取文件列表
     */
    public List<FileInfo> getFileList(File repoDir, String branchName, String path) throws Exception {
        List<FileInfo> files = new ArrayList<>();
        
        logger.debug("Getting file list for repository: {}, branch: {}, path: {}", 
            repoDir.getAbsolutePath(), branchName, path);
        
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(repoDir)
                .setMustExist(true)
                .build()) {
            
            try (Git git = new Git(repository)) {
                // 如果没有指定分支，使用默认分支
                if (branchName == null || branchName.isEmpty()) {
                    branchName = getDefaultBranch(repository);
                    if (branchName == null) {
                        logger.warn("No default branch found for repository: {}", repoDir.getAbsolutePath());
                        return files;
                    }
                }
                
                // 确保分支名称格式正确
                if (!branchName.startsWith("refs/heads/")) {
                    branchName = "refs/heads/" + branchName;
                }
                
                ObjectId branchId = repository.resolve(branchName);
                if (branchId == null) {
                    logger.warn("Branch {} not found in repository: {}", branchName, repoDir.getAbsolutePath());
                    return files;
                }
                
                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    treeWalk.addTree(repository.parseCommit(branchId).getTree());
                    treeWalk.setRecursive(false);
                    
                    // 如果指定了路径，进入该路径
                    if (path != null && !path.isEmpty() && !"/".equals(path)) {
                        treeWalk.setFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(path));
                        if (treeWalk.next()) {
                            treeWalk.enterSubtree();
                        }
                    }
                    
                    while (treeWalk.next()) {
                        FileInfo info = new FileInfo();
                        info.setName(treeWalk.getNameString());
                        info.setPath(treeWalk.getPathString());
                        info.setType(treeWalk.isSubtree() ? "directory" : "file");
                        
                        if (!treeWalk.isSubtree()) {
                            // 获取文件大小
                            try {
                                long size = repository.open(treeWalk.getObjectId(0)).getSize();
                                info.setSize(size);
                                info.setSizeFormatted(formatBytes(size));
                            } catch (Exception e) {
                                info.setSize(0);
                                info.setSizeFormatted("0 B");
                                logger.debug("Failed to get size for file {}: {}", info.getName(), e.getMessage());
                            }
                        } else {
                            info.setSize(0);
                            info.setSizeFormatted("-");
                        }
                        
                        files.add(info);
                    }
                }
                
                logger.debug("Found {} files in repository", files.size());
            }
        }
        
        // 排序：目录在前，然后按名称排序
        files.sort((a, b) -> {
            if (a.getType().equals(b.getType())) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
            return "directory".equals(a.getType()) ? -1 : 1;
        });
        
        return files;
    }

    /**
     * 获取默认分支
     */
    private String getDefaultBranch(Repository repository) throws IOException {
        // 首先检查HEAD的符号引用
        Ref head = repository.exactRef("HEAD");
        if (head != null && head.isSymbolic() && head.getTarget() != null) {
            String targetName = head.getTarget().getName();
            logger.debug("HEAD points to symbolic ref: {}", targetName);
            return targetName;
        }
        
        // 如果HEAD直接指向提交对象，尝试找到指向同一提交的分支
        if (head != null && head.getObjectId() != null) {
            ObjectId headObjectId = head.getObjectId();
            logger.debug("HEAD points to commit: {}", headObjectId.getName());
            
            // 尝试常见的默认分支名
            for (String branch : Arrays.asList("refs/heads/master", "refs/heads/main")) {
                Ref branchRef = repository.exactRef(branch);
                if (branchRef != null && headObjectId.equals(branchRef.getObjectId())) {
                    logger.debug("Found matching branch: {}", branch);
                    return branch;
                }
            }
            
            // 如果没有找到匹配的，返回第一个分支
            try {
                Map<String, Ref> refs = repository.getAllRefs();
                for (Map.Entry<String, Ref> entry : refs.entrySet()) {
                    if (entry.getKey().startsWith("refs/heads/") && 
                        headObjectId.equals(entry.getValue().getObjectId())) {
                        logger.debug("Found first matching branch: {}", entry.getKey());
                        return entry.getKey();
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to get all refs: {}", e.getMessage());
            }
        }
        
        // 最后尝试：返回第一个存在的分支
        for (String branch : Arrays.asList("refs/heads/master", "refs/heads/main")) {
            if (repository.exactRef(branch) != null) {
                logger.debug("Using fallback branch: {}", branch);
                return branch;
            }
        }
        
        logger.warn("No default branch found for repository: {}", repository.getDirectory());
        return null;
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

    /**
     * 检查仓库是否为空
     */
    public boolean isEmptyRepository(File repoDir) {
        try {
            try (Repository repository = new FileRepositoryBuilder()
                    .setGitDir(repoDir)
                    .setMustExist(true)
                    .build()) {
                
                Ref head = repository.exactRef("HEAD");
                boolean isEmpty = (head == null || head.getObjectId() == null);
                logger.debug("Repository {} is empty: {}", repoDir.getAbsolutePath(), isEmpty);
                return isEmpty;
            }
        } catch (Exception e) {
            logger.warn("Failed to check if repository is empty: {}", e.getMessage());
            return true;
        }
    }
}