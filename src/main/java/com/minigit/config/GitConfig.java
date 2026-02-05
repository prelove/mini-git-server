package com.minigit.config;

import com.minigit.git.CustomRepositoryResolver;
import org.eclipse.jgit.http.server.GitServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Git HTTP service configuration.
 */
@Configuration
public class GitConfig {

    private final CustomRepositoryResolver repositoryResolver;

    public GitConfig(CustomRepositoryResolver repositoryResolver) {
        this.repositoryResolver = repositoryResolver;
    }

    /**
     * Register Git servlet.
     */
    @Bean
    public ServletRegistrationBean<GitServlet> gitServletRegistration() {
        GitServlet gitServlet = new GitServlet();
        
        // Set repository resolver.
        gitServlet.setRepositoryResolver(repositoryResolver);
        
        // Enable receive-pack (push).
        gitServlet.setReceivePackFactory((req, db) -> {
            // Add push authorization checks here if needed.
            return new org.eclipse.jgit.transport.ReceivePack(db);
        });
        
        // Enable upload-pack (fetch/clone).
        gitServlet.setUploadPackFactory((req, db) -> {
            // Add fetch authorization checks here if needed.
            return new org.eclipse.jgit.transport.UploadPack(db);
        });

        ServletRegistrationBean<GitServlet> registration = new ServletRegistrationBean<>(gitServlet, "/git/*");
        registration.setName("GitServlet");
        registration.setLoadOnStartup(1);
        
        return registration;
    }
}
