package com.minigit.service;

import java.io.File;
import java.util.List;

/**
 * Repository service interface.
 */
public interface RepositoryService {

    /**
     * Create a bare repository.
     * @param name repository name
     * @return created repository directory
     */
    File createRepository(String name);

    /**
     * List all repositories.
     * @return repository name list
     */
    List<String> listRepositories();

    /**
     * Check whether a repository exists.
     * @param name repository name
     * @return whether it exists
     */
    boolean repositoryExists(String name);

    /**
     * Get repository path.
     * @param name repository name
     * @return repository file object
     */
    File getRepositoryPath(String name);

    /**
     * Validate repository name.
     * @param name repository name
     * @return whether it is valid
     */
    boolean isValidRepositoryName(String name);

    /**
     * Normalize repository name (add .git suffix, etc).
     * @param name original name
     * @return normalized name
     */
    String normalizeRepositoryName(String name);
}
