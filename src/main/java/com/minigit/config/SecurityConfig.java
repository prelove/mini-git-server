package com.minigit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * 安全配置
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final VcsProperties vcsProperties;

    public SecurityConfig(VcsProperties vcsProperties) {
        this.vcsProperties = vcsProperties;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .authorizeRequests()
                .antMatchers("/debug/**").permitAll() // 临时允许调试端点
                .antMatchers("/css/**", "/js/**", "/images/**").permitAll() // 静态资源
                .antMatchers("/login").permitAll() // 登录页面
                .anyRequest().authenticated()
            .and()
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
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            .and()
            .httpBasic(); // 保留HTTP Basic认证用于API和Git操作
    }

    @Bean
    @Override
    public UserDetailsService userDetailsService() {
        UserDetails user = User.builder()
            .username(vcsProperties.getAuth().getUser())
            .password(passwordEncoder().encode(vcsProperties.getAuth().getPass()))
            .roles("USER", "ADMIN")
            .build();

        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}