package com.minigit.dto;

import java.time.LocalDateTime;

/**
 * Represents a public file share link with optional expiry and password protection.
 */
public class ShareLink {

    private final String token;
    private final String repoName;
    private final String filePath;
    private final String branch;
    private final String fileName;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt; // null = never expires
    private final String passwordHash;     // SHA-256 hex; null = no password
    private volatile boolean active;

    public ShareLink(String token, String repoName, String filePath, String branch,
                     String fileName, LocalDateTime createdAt, LocalDateTime expiresAt,
                     String passwordHash) {
        this.token = token;
        this.repoName = repoName;
        this.filePath = filePath;
        this.branch = branch;
        this.fileName = fileName;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.passwordHash = passwordHash;
        this.active = true;
    }

    public String getToken() { return token; }
    public String getRepoName() { return repoName; }
    public String getFilePath() { return filePath; }
    public String getBranch() { return branch; }
    public String getFileName() { return fileName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    /** Returns true if the link has a configured expiry that has already passed. */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /** Returns true if the link is active AND not expired. */
    public boolean isAccessible() {
        return active && !isExpired();
    }
}
