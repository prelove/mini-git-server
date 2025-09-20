package com.minigit.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
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
                ObjectId startId = resolveBranchObjectId(repository, branchName);

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

            ObjectId branchId = resolveBranchObjectId(repository, branchName);
            if (branchId == null) {
                logger.warn("Branch {} not found in repository: {}", branchName, repoDir.getAbsolutePath());
                return files;
            }

            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(branchId);
                RevTree tree = commit.getTree();

                String normalizedPath = normalizePath(path);

                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    if (!normalizedPath.isEmpty()) {
                        try (TreeWalk dirWalk = TreeWalk.forPath(repository, normalizedPath, tree)) {
                            if (dirWalk == null || !dirWalk.isSubtree()) {
                                logger.debug("Path {} not found in repository", normalizedPath);
                                return files;
                            }
                            treeWalk.addTree(dirWalk.getObjectId(0));
                        }
                    } else {
                        treeWalk.addTree(tree);
                    }

                    treeWalk.setRecursive(false);

                    while (treeWalk.next()) {
                        FileInfo info = new FileInfo();
                        info.setName(treeWalk.getNameString());
                        String filePath = treeWalk.getPathString();
                        if (!normalizedPath.isEmpty()) {
                            filePath = normalizedPath + "/" + treeWalk.getNameString();
                        }
                        info.setPath(filePath);

                        boolean isDirectory = treeWalk.isSubtree();
                        info.setType(isDirectory ? "directory" : "file");

                        if (isDirectory) {
                            info.setSize(0);
                            info.setSizeFormatted("-");
                        } else {
                            try {
                                ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
                                long size = loader.getSize();
                                info.setSize(size);
                                info.setSizeFormatted(formatBytes(size));
                            } catch (Exception e) {
                                info.setSize(0);
                                info.setSizeFormatted("0 B");
                                logger.debug("Failed to get size for file {}: {}", info.getName(), e.getMessage());
                            }
                        }

                        files.add(info);
                    }
                }
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
     * 获取文件详细信息
     */
    public FileInfo getFileInfo(File repoDir, String branchName, String path) throws Exception {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("File path must not be empty");
        }

        String normalizedPath = normalizePath(path);
        if (normalizedPath.isEmpty()) {
            throw new IllegalArgumentException("File path must not point to repository root");
        }

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(repoDir)
                .setMustExist(true)
                .build()) {

            ObjectId branchId = resolveBranchObjectId(repository, branchName);
            if (branchId == null) {
                throw new IllegalArgumentException("Branch not found: " + (branchName == null ? "(default)" : branchName));
            }

            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(branchId);
                RevTree tree = commit.getTree();

                try (TreeWalk treeWalk = TreeWalk.forPath(repository, normalizedPath, tree)) {
                    if (treeWalk == null) {
                        throw new IllegalArgumentException("File not found: " + path);
                    }

                    FileInfo info = new FileInfo();
                    info.setName(treeWalk.getNameString());
                    info.setPath(normalizedPath);

                    if (treeWalk.isSubtree()) {
                        info.setType("directory");
                        info.setSize(0);
                        info.setSizeFormatted("-");
                    } else {
                        info.setType("file");
                        ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
                        long size = loader.getSize();
                        info.setSize(size);
                        info.setSizeFormatted(formatBytes(size));
                    }

                    return info;
                }
            }
        }
    }

    /**
     * 获取文件内容
     */
    public byte[] getFileContent(File repoDir, String branchName, String path) throws Exception {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("File path must not be empty");
        }

        String normalizedPath = normalizePath(path);
        if (normalizedPath.isEmpty()) {
            throw new IllegalArgumentException("File path must not point to repository root");
        }

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(repoDir)
                .setMustExist(true)
                .build()) {

            ObjectId branchId = resolveBranchObjectId(repository, branchName);
            if (branchId == null) {
                throw new IllegalArgumentException("Branch not found: " + (branchName == null ? "(default)" : branchName));
            }

            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(branchId);
                RevTree tree = commit.getTree();

                try (TreeWalk treeWalk = TreeWalk.forPath(repository, normalizedPath, tree)) {
                    if (treeWalk == null || treeWalk.isSubtree()) {
                        throw new IllegalArgumentException("File not found or is a directory: " + path);
                    }

                    ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
                    return loader.getBytes();
                }
            }
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

    /**
     * 创建新分支
     */
    public void createBranch(File repoDir, String sourceBranch, String newBranch) throws Exception {
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(repoDir)
                .setMustExist(true)
                .build();
             Git git = new Git(repository)) {

            if (sourceBranch == null || sourceBranch.isEmpty()) {
                sourceBranch = getDefaultBranch(repository);
                if (sourceBranch == null) {
                    throw new IllegalArgumentException("Source branch not found");
                }
            }

            if (!sourceBranch.startsWith("refs/heads/")) {
                sourceBranch = "refs/heads/" + sourceBranch;
            }

            if (repository.resolve(sourceBranch) == null) {
                throw new IllegalArgumentException("Source branch not found: " + sourceBranch);
            }

            git.branchCreate()
                .setName(newBranch)
                .setStartPoint(sourceBranch)
                .call();
        }
    }

    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String trimmed = path.trim();
        if (trimmed.isEmpty() || "/".equals(trimmed)) {
            return "";
        }
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private ObjectId resolveBranchObjectId(Repository repository, String branchName) throws IOException {
        if (branchName != null && !branchName.trim().isEmpty()) {
            ObjectId direct = repository.resolve(branchName);
            if (direct != null) {
                return direct;
            }
        }

        String resolved = branchName;
        if (resolved == null || resolved.trim().isEmpty()) {
            resolved = getDefaultBranch(repository);
        }

        if (resolved == null || resolved.trim().isEmpty()) {
            return null;
        }

        if (!resolved.startsWith("refs/")) {
            resolved = "refs/heads/" + resolved;
        }

        ObjectId branchId = repository.resolve(resolved);
        if (branchId == null && branchName != null && branchName.startsWith("refs/")) {
            branchId = repository.resolve(branchName);
        }

        return branchId;
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
}
