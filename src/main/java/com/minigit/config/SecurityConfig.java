package com.minigit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                // 1) Git 端点不要被 CSRF 拦, 否则 push 会 403
                .csrf().ignoringAntMatchers("/git/**", "/login").and()

                // 2) 授权规则：/git/** 需要认证；静态资源与 /login 放行
                .authorizeRequests()
                .antMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/", "/login").permitAll()
                .antMatchers("/git/**").authenticated()
                .anyRequest().authenticated()
                .and()

                // 3) 为 /git/** 提供 HTTP Basic（401 + WWW-Authenticate）
                .httpBasic()
                .and()

                // 4) 仍然保留网站的表单登录（用于非 /git/** 的管理页面）
                .formLogin()
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/admin", true)
                .failureUrl("/login?error=true")
                .usernameParameter("username")
                .passwordParameter("password")
                .permitAll()
                .and()
                .logout()
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .permitAll()
                .and()

                // 5) 关键：把 /git/** 的未认证入口改成 Basic，而不是重定向到 /login
                .exceptionHandling()
                .defaultAuthenticationEntryPointFor(
                        gitBasicEntryPoint(), new AntPathRequestMatcher("/git/**"));
    }

    @Bean
    public BasicAuthenticationEntryPoint gitBasicEntryPoint() {
        BasicAuthenticationEntryPoint ep = new BasicAuthenticationEntryPoint();
        ep.setRealmName("mini-git");
        return ep;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 支持 {bcrypt}、{noop} 等多种编码前缀的“委派编码器”
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        // 测试账号：git / 123456
        return new InMemoryUserDetailsManager(
                User.withUsername("admin")
                        .password(encoder.encode("admin123"))
                        .roles("GIT")
                        .build()
        );
    }

}
