package com.minigit.service.impl;

import com.minigit.config.VcsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RepositoryServiceImpl.
 */
class RepositoryServiceImplTest {

    @TempDir
    Path tempDir;

    private RepositoryServiceImpl service;

    @BeforeEach
    void setUp() {
        VcsProperties props = new VcsProperties();
        props.getStorage().setDir(tempDir.toString());
        service = new RepositoryServiceImpl(props);
        service.init();
    }

    // --- normalizeRepositoryName ---

    @Test
    void normalizeAddsGitSuffix() {
        assertEquals("my-repo.git", service.normalizeRepositoryName("my-repo"));
    }

    @Test
    void normalizeKeepsExistingGitSuffix() {
        assertEquals("my-repo.git", service.normalizeRepositoryName("my-repo.git"));
    }

    @Test
    void normalizeTrimsBlanks() {
        assertEquals("repo.git", service.normalizeRepositoryName("  repo  "));
    }

    // --- isValidRepositoryName ---

    @Test
    void validNamesAccepted() {
        assertTrue(service.isValidRepositoryName("my-repo"));
        assertTrue(service.isValidRepositoryName("my_repo"));
        assertTrue(service.isValidRepositoryName("MyRepo123"));
        assertTrue(service.isValidRepositoryName("my-repo.git"));
    }

    @Test
    void invalidNamesRejected() {
        assertFalse(service.isValidRepositoryName(null));
        assertFalse(service.isValidRepositoryName(""));
        assertFalse(service.isValidRepositoryName("   "));
        assertFalse(service.isValidRepositoryName("my repo"));    // space
        assertFalse(service.isValidRepositoryName("my/repo"));    // slash
        assertFalse(service.isValidRepositoryName("../secret"));  // path traversal
    }

    // --- createRepository / listRepositories / repositoryExists ---

    @Test
    void createRepositoryCreatesBareRepo() {
        File repo = service.createRepository("test-repo");
        assertTrue(repo.exists());
        assertTrue(repo.isDirectory());
        // A bare repository contains HEAD
        assertTrue(new File(repo, "HEAD").exists());
    }

    @Test
    void createRepositoryNormalizesName() {
        File repo = service.createRepository("proj");
        assertEquals("proj.git", repo.getName());
    }

    @Test
    void createRepositoryThrowsOnDuplicate() {
        service.createRepository("dup");
        assertThrows(RuntimeException.class, () -> service.createRepository("dup"));
    }

    @Test
    void listRepositoriesReturnsCreatedRepos() {
        service.createRepository("alpha");
        service.createRepository("beta");
        List<String> list = service.listRepositories();
        assertTrue(list.contains("alpha.git"));
        assertTrue(list.contains("beta.git"));
    }

    @Test
    void listRepositoriesReturnsEmptyWhenNone() {
        List<String> list = service.listRepositories();
        assertTrue(list.isEmpty());
    }

    @Test
    void repositoryExistsReturnsTrueAfterCreation() {
        service.createRepository("exists-check");
        assertTrue(service.repositoryExists("exists-check.git"));
        assertTrue(service.repositoryExists("exists-check"));  // normalizes automatically
    }

    @Test
    void repositoryExistsReturnsFalseForMissing() {
        assertFalse(service.repositoryExists("no-such-repo"));
    }

    @Test
    void getRepositoryPathReturnsCorrectPath() {
        File path = service.getRepositoryPath("my-repo");
        assertEquals(new File(tempDir.toFile(), "my-repo.git"), path);
    }
}
