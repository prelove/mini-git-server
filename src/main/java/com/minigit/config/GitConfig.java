package com.minigit.config;

import com.minigit.git.CustomRepositoryResolver;
import org.eclipse.jgit.http.server.GitServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Git HTTP服务配置
 */
@Configuration
public class GitConfig {

    private final CustomRepositoryResolver repositoryResolver;

    public GitConfig(CustomRepositoryResolver repositoryResolver) {
        this.repositoryResolver = repositoryResolver;
    }

    /**
     * 注册Git Servlet
     */
    @Bean
    public ServletRegistrationBean<GitServlet> gitServletRegistration() {
        GitServlet gitServlet = new GitServlet();
        
        // 设置仓库解析器
        gitServlet.setRepositoryResolver(repositoryResolver);
        
        // 启用接收包服务（push）
        gitServlet.setReceivePackFactory((req, db) -> {
            // 这里可以添加推送权限检查
            return new org.eclipse.jgit.transport.ReceivePack(db);
        });
        
        // 启用上传包服务（fetch/clone）
        gitServlet.setUploadPackFactory((req, db) -> {
            // 这里可以添加拉取权限检查
            return new org.eclipse.jgit.transport.UploadPack(db);
        });

        ServletRegistrationBean<GitServlet> registration = new ServletRegistrationBean<>(gitServlet, "/git/*");
        registration.setName("GitServlet");
        registration.setLoadOnStartup(1);
        
        return registration;
    }
}