package com.minigit.service;

import com.minigit.dto.ShareLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for public file share links.
 */
@Service
public class ShareLinkService {

    private static final Logger logger = LoggerFactory.getLogger(ShareLinkService.class);

    private final ConcurrentHashMap<String, ShareLink> store = new ConcurrentHashMap<>();

    /**
     * Create a new share link.
     *
     * @param repoName       repository name
     * @param filePath       file path within the repository
     * @param branch         branch name (may be null)
     * @param fileName       display file name
     * @param expiresInHours hours until expiry; 0 = never
     * @param password       plain-text password; null or empty = no password
     * @return newly created ShareLink
     */
    public ShareLink createShareLink(String repoName, String filePath, String branch,
                                     String fileName, long expiresInHours, String password) {
        String token = UUID.randomUUID().toString();
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime expiresAt = expiresInHours > 0 ? createdAt.plusHours(expiresInHours) : null;
        String passwordHash = (password != null && !password.isEmpty()) ? sha256(password) : null;

        ShareLink link = new ShareLink(token, repoName, filePath, branch, fileName,
                createdAt, expiresAt, passwordHash);
        store.put(token, link);
        logger.info("Share link created: token={} repo={} path={} expiresAt={} hasPassword={}",
                token, repoName, filePath, expiresAt, passwordHash != null);
        return link;
    }

    /**
     * Look up a share link by token. Returns null if not found.
     */
    public ShareLink getByToken(String token) {
        if (token == null) return null;
        return store.get(token);
    }

    /**
     * Validate that a token is accessible and the password (if required) matches.
     *
     * @param token    share token
     * @param password plain-text password (may be null)
     * @return true if access is permitted
     */
    public boolean validateAccess(String token, String password) {
        ShareLink link = getByToken(token);
        if (link == null || !link.isAccessible()) return false;
        if (link.getPasswordHash() == null) return true;
        if (password == null || password.isEmpty()) return false;
        return link.getPasswordHash().equals(sha256(password));
    }

    /**
     * Revoke (deactivate) a share link. No-op if not found.
     */
    public void revokeToken(String token) {
        ShareLink link = store.get(token);
        if (link != null) {
            link.setActive(false);
            logger.info("Share link revoked: token={}", token);
        }
    }

    /**
     * Return all share links, most recently created first.
     */
    public List<ShareLink> listAll() {
        List<ShareLink> result = new ArrayList<>(store.values());
        result.sort(Comparator.comparing(ShareLink::getCreatedAt, Comparator.reverseOrder()));
        return Collections.unmodifiableList(result);
    }

    /** SHA-256 hex digest of a string. */
    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
