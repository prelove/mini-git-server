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
                // 1) Git endpoints must bypass CSRF, otherwise push will 403.
                .csrf().ignoringAntMatchers("/git/**", "/login").and()

                // 2) Authorization: /git/** requires auth; static resources and /login are public.
                .authorizeRequests()
                .antMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/", "/login").permitAll()
                .antMatchers("/git/**").authenticated()
                .anyRequest().authenticated()
                .and()

                // 3) Provide HTTP Basic for /git/** (401 + WWW-Authenticate).
                .httpBasic()
                .and()

                // 4) Keep form login for the admin pages (non /git/**).
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

                // 5) Key: unauthenticated /git/** should be Basic, not redirect to /login.
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
        // Delegating encoder supports prefixes like {bcrypt}, {noop}, etc.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        // Test account: git / 123456
        return new InMemoryUserDetailsManager(
                User.withUsername("admin")
                        .password(encoder.encode("admin123"))
                        .roles("GIT")
                        .build()
        );
    }

}
