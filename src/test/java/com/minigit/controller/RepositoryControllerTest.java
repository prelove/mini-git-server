package com.minigit.controller;

import com.minigit.service.RepositoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for RepositoryController REST API.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RepositoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RepositoryService repositoryService;

    // --- GET /api/repos ---

    @Test
    @WithMockUser
    void listRepositoriesReturnsJsonArray() throws Exception {
        when(repositoryService.listRepositories()).thenReturn(Arrays.asList("alpha.git", "beta.git"));

        mockMvc.perform(get("/api/repos").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("alpha.git"))
                .andExpect(jsonPath("$[1]").value("beta.git"));
    }

    @Test
    @WithMockUser
    void listRepositoriesReturnsEmptyArray() throws Exception {
        when(repositoryService.listRepositories()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/repos").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // --- POST /api/repos ---

    @Test
    @WithMockUser
    void createRepositorySucceeds() throws Exception {
        when(repositoryService.isValidRepositoryName("my-repo")).thenReturn(true);
        when(repositoryService.normalizeRepositoryName("my-repo")).thenReturn("my-repo.git");
        when(repositoryService.repositoryExists("my-repo.git")).thenReturn(false);
        when(repositoryService.createRepository("my-repo")).thenReturn(new File("/tmp/my-repo.git"));

        mockMvc.perform(post("/api/repos")
                        .with(csrf())
                        .param("name", "my-repo")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("my-repo.git"));
    }

    @Test
    @WithMockUser
    void createRepositoryReturnsBadRequestForEmptyName() throws Exception {
        mockMvc.perform(post("/api/repos")
                        .with(csrf())
                        .param("name", "")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void createRepositoryReturnsBadRequestForInvalidName() throws Exception {
        when(repositoryService.isValidRepositoryName("bad name")).thenReturn(false);

        mockMvc.perform(post("/api/repos")
                        .with(csrf())
                        .param("name", "bad name")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_NAME"));
    }

    @Test
    @WithMockUser
    void createRepositoryReturnsBadRequestForDuplicate() throws Exception {
        when(repositoryService.isValidRepositoryName("existing")).thenReturn(true);
        when(repositoryService.normalizeRepositoryName("existing")).thenReturn("existing.git");
        when(repositoryService.repositoryExists("existing.git")).thenReturn(true);

        mockMvc.perform(post("/api/repos")
                        .with(csrf())
                        .param("name", "existing")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("REPO_ALREADY_EXISTS"));
    }

    @Test
    void listRepositoriesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/repos").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }
}
