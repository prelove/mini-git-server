package com.minigit.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.ListBranchCommand;
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

    // -------- DTOs --------

    public static class CommitInfo {
        private String id;
        private String shortId;
        private String message;
        private String author;
        private String email;
        private Date date;
        private String dateFormatted;

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

    public static class FileInfo {
        private String name;
        private String path;
        private String type; // "file" or "directory"
        private long size;
        private String sizeFormatted;

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

    public static class BranchInfo {
        private String name;
        private String shortName;
        private boolean isDefault;
        private String lastCommitId;
        private String lastCommitMessage;
        private Date lastCommitDate;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getShortName() { return shortName; }
        public void setShortName(String shortName) { this.shortName = shortName; }
        public boolean isDefault() { return isDefault; }
        public void setDefault(boolean aDefault) { isDefault = aDefault; }
        public String getLastCommitId() { return lastCommitId; }
        public void setLastCommitId(String lastCommitId) { this.lastCommitId = lastCommitId; }
        public String getLastCommitMessage() { return lastCommitMessage; }
        public void setLastCommitMessage(String lastCommitMessage) { this.lastCommitMessage = lastCommitMessage; }
        public Date getLastCommitDate() { return lastCommitDate; }
        public void setLastCommitDate(Date lastCommitDate) { this.lastCommitDate = lastCommitDate; }
    }

    // -------- Public API --------

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

    public List<CommitInfo> getCommitLog(File repoDir, int maxCount) throws Exception {
        List<CommitInfo> commits = new ArrayList<>();
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(repoDir)
                .setMustExist(true)
                .build()) {
            try (Git git = new Git(repository)) {
                Ref head = repository.exactRef("HEAD");
                if (head == null || head.getObjectId() == null) {
                    return commits;
                }
                LogCommand lc = git.log().add(head.getObjectId()).setMaxCount(maxCount);
                for (RevCommit commit : lc.call()) {
                    commits.add(toCommitInfo(commit));
                }
            }
        }
        return commits;
    }

    public List<CommitInfo> getCommitLog(File repoDir, String branchName, int maxCount) throws Exception {
        List<CommitInfo> commits = new ArrayList<>();
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(repoDir)
                .setMustExist(true)
                .build()) {
            try (Git git = new Git(repository)) {
                ObjectId startId = resolveBranchObjectId(repository, branchName);
                if (startId == null) {
                    return commits;
                }
                LogCommand lc = git.log().add(startId).setMaxCount(maxCount);
                for (RevCommit commit : lc.call()) {
                    commits.add(toCommitInfo(commit));
                }
            }
        }
        return commits;
    }

    public List<BranchInfo> getBranches(File repoDir) throws Exception {
        List<BranchInfo> branches = new ArrayList<>();
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(repoDir)
                .setMustExist(true)
                .build()) {
            try (Git git = new Git(repository)) {
                List<Ref> localRefs = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
                String defaultBranch = getDefaultBranch(repository);
                for (Ref ref : localRefs) {
                    if (!ref.getName().startsWith("refs/heads/")) continue;
                    BranchInfo info = new BranchInfo();
                    info.setName(ref.getName());
                    info.setShortName(Repository.shortenRefName(ref.getName()));
                    info.setDefault(ref.getName().equals(defaultBranch));
                    ObjectId objectId = ref.getObjectId();
                    if (objectId != null) {
                        try {
                            Iterable<RevCommit> commits = git.log().add(objectId).setMaxCount(1).call();
                            for (RevCommit commit : commits) {
                                info.setLastCommitId(commit.getId().abbreviate(8).name());
                                info.setLastCommitMessage(commit.getShortMessage());
                                info.setLastCommitDate(commit.getAuthorIdent().getWhen());
                                break;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    branches.add(info);
                }
            }
        }
        return branches;
    }

    public List<FileInfo> getFileList(File repoDir, String branchName, String path) throws Exception {
        List<FileInfo> files = new ArrayList<>();
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(repoDir)
                .setMustExist(true)
                .build()) {

            ObjectId branchId = resolveBranchObjectId(repository, branchName);
            if (branchId == null) {
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
                            }
                        }
                        files.add(info);
                    }
                }
            }
        }

        files.sort((a, b) -> {
            if (a.getType().equals(b.getType())) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
            return "directory".equals(a.getType()) ? -1 : 1;
        });
        return files;
    }

// 在 GitRepositoryService 中修改 isEmptyRepository 方法
public boolean isEmptyRepository(File repoDir) throws Exception {
    try (Repository repository = Git.open(repoDir).getRepository()) {
        // 检查是否有任何引用
        Collection<Ref> refs = repository.getRefDatabase().getRefs();

        // 过滤掉非分支引用，只看 heads
        boolean hasBranches = refs.stream()
            .anyMatch(ref -> ref.getName().startsWith("refs/heads/"));

        if (!hasBranches) {
            return true;
        }
        
        // 进一步检查是否有实际的提交
        try {
            ObjectId headId = repository.resolve("HEAD");
            if (headId != null) {
                return false; // 有 HEAD 指向的提交
            }

            // 如果 HEAD 为空，检查所有分支
            for (Ref ref : refs) {
                if (ref.getName().startsWith("refs/heads/")) {
                    ObjectId objectId = ref.getObjectId();
                    if (objectId != null) {
                        return false; // 找到有效的分支提交
                    }
                }
            }
        } catch (Exception e) {
            // 如果无法解析提交，可能确实是空仓库
            logger.debug("Cannot resolve commits in repository: " + e.getMessage());
        }

        return true;
    }
}

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

            git.branchCreate().setName(newBranch).setStartPoint(sourceBranch).call();
        }
    }

    // -------- Helpers --------

    private CommitInfo toCommitInfo(RevCommit commit) {
        CommitInfo info = new CommitInfo();
        info.setId(commit.getId().getName());
        info.setShortId(commit.getId().abbreviate(8).name());
        info.setMessage(commit.getShortMessage());
        info.setAuthor(commit.getAuthorIdent().getName());
        info.setEmail(commit.getAuthorIdent().getEmailAddress());
        info.setDate(commit.getAuthorIdent().getWhen());
        info.setDateFormatted(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(info.getDate()));
        return info;
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

    private String getDefaultBranch(Repository repository) throws IOException {
        Ref head = repository.exactRef("HEAD");
        if (head != null && head.isSymbolic() && head.getTarget() != null) {
            return head.getTarget().getName();
        }
        if (head != null && head.getObjectId() != null) {
            ObjectId headObjectId = head.getObjectId();
            for (String branch : Arrays.asList("refs/heads/master", "refs/heads/main")) {
                Ref branchRef = repository.exactRef(branch);
                if (branchRef != null && headObjectId.equals(branchRef.getObjectId())) {
                    return branch;
                }
            }
            try {
                Map<String, Ref> refs = repository.getAllRefs();
                for (Map.Entry<String, Ref> entry : refs.entrySet()) {
                    if (entry.getKey().startsWith("refs/heads/") && headObjectId.equals(entry.getValue().getObjectId())) {
                        return entry.getKey();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        for (String branch : Arrays.asList("refs/heads/master", "refs/heads/main")) {
            if (repository.exactRef(branch) != null) {
                return branch;
            }
        }
        return null;
    }

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
