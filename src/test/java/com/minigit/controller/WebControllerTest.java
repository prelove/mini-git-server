package com.minigit.controller;

import com.minigit.service.GitRepositoryService;
import com.minigit.service.RepositoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the web admin UI (WebController).
 */
@SpringBootTest
@AutoConfigureMockMvc
class WebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RepositoryService repositoryService;

    @MockBean
    private GitRepositoryService gitRepositoryService;

    // --- GET / ---

    @Test
    @WithMockUser
    void rootRedirectsToAdmin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    // --- GET /admin ---

    @Test
    void adminPageRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser
    void adminPageListsRepositories() throws Exception {
        when(repositoryService.listRepositories()).thenReturn(Arrays.asList("alpha.git", "beta.git"));
        when(repositoryService.getStorageDir()).thenReturn(new File("/tmp/repos"));

        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/index"))
                .andExpect(model().attributeExists("repositories"))
                .andExpect(model().attribute("repoCount", 2));
    }

    @Test
    @WithMockUser
    void adminPageShowsEmptyList() throws Exception {
        when(repositoryService.listRepositories()).thenReturn(Collections.emptyList());
        when(repositoryService.getStorageDir()).thenReturn(new File("/tmp/repos"));

        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("repoCount", 0));
    }

    // --- GET /admin/create ---

    @Test
    @WithMockUser
    void createPageReturnsForm() throws Exception {
        mockMvc.perform(get("/admin/create"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/create"));
    }

    // --- POST /admin/create ---

    @Test
    @WithMockUser
    void createRepositoryRedirectsToAdminOnSuccess() throws Exception {
        when(repositoryService.isValidRepositoryName("newrepo")).thenReturn(true);
        when(repositoryService.normalizeRepositoryName("newrepo")).thenReturn("newrepo.git");
        when(repositoryService.repositoryExists("newrepo.git")).thenReturn(false);
        when(repositoryService.createRepository("newrepo")).thenReturn(new File("/tmp/repos/newrepo.git"));

        mockMvc.perform(post("/admin/create").with(csrf()).param("name", "newrepo"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    @WithMockUser
    void createRepositoryRedirectsBackOnInvalidName() throws Exception {
        when(repositoryService.isValidRepositoryName("bad name")).thenReturn(false);

        mockMvc.perform(post("/admin/create").with(csrf()).param("name", "bad name"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/create"));
    }

    // --- POST /admin/repo/{name}/delete ---

    @Test
    @WithMockUser
    void deleteRepositoryRedirectsToAdmin() throws Exception {
        when(repositoryService.normalizeRepositoryName("myrepo")).thenReturn("myrepo.git");
        when(repositoryService.repositoryExists("myrepo.git")).thenReturn(true);
        doNothing().when(repositoryService).deleteRepository("myrepo");

        mockMvc.perform(post("/admin/repo/myrepo/delete").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    @WithMockUser
    void deleteRepositoryRedirectsWithErrorWhenNotFound() throws Exception {
        when(repositoryService.normalizeRepositoryName("missing")).thenReturn("missing.git");
        when(repositoryService.repositoryExists("missing.git")).thenReturn(false);

        mockMvc.perform(post("/admin/repo/missing/delete").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    // --- POST /admin/repo/{name}/branch ---

    @Test
    @WithMockUser
    void createBranchRedirectsToRepoOnSuccess() throws Exception {
        when(repositoryService.normalizeRepositoryName("myrepo")).thenReturn("myrepo.git");
        when(repositoryService.getRepositoryPath("myrepo.git")).thenReturn(new File("/tmp/repos/myrepo.git"));
        doNothing().when(gitRepositoryService).createBranch(any(File.class), anyString(), anyString());

        mockMvc.perform(post("/admin/repo/myrepo/branch").with(csrf())
                        .param("fromBranch", "main")
                        .param("newBranch", "feature-x"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser
    void createBranchRejectsInvalidBranchName() throws Exception {
        mockMvc.perform(post("/admin/repo/myrepo/branch").with(csrf())
                        .param("fromBranch", "main")
                        .param("newBranch", "bad..name"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/repo/myrepo?branch=main"));
    }

    // --- POST /admin/repo/{name}/branch/delete ---

    @Test
    @WithMockUser
    void deleteBranchRedirectsToRepo() throws Exception {
        when(repositoryService.normalizeRepositoryName("myrepo")).thenReturn("myrepo.git");
        when(repositoryService.getRepositoryPath("myrepo.git")).thenReturn(new File("/tmp/repos/myrepo.git"));
        doNothing().when(gitRepositoryService).deleteBranch(any(File.class), anyString());

        mockMvc.perform(post("/admin/repo/myrepo/branch/delete").with(csrf())
                        .param("branchName", "feature-x"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/repo/myrepo.git"));
    }

    @Test
    @WithMockUser
    void deleteBranchHandlesDefaultBranchError() throws Exception {
        when(repositoryService.normalizeRepositoryName("myrepo")).thenReturn("myrepo.git");
        when(repositoryService.getRepositoryPath("myrepo.git")).thenReturn(new File("/tmp/repos/myrepo.git"));
        doThrow(new IllegalArgumentException("default.branch"))
                .when(gitRepositoryService).deleteBranch(any(File.class), eq("main"));

        mockMvc.perform(post("/admin/repo/myrepo/branch/delete").with(csrf())
                        .param("branchName", "main"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/repo/myrepo"));
    }
}
