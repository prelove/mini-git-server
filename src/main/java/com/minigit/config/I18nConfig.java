package com.minigit.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.Locale;

/**
 * Internationalization configuration.
 */
@Configuration
public class I18nConfig implements WebMvcConfigurer {

    private final VcsProperties vcsProperties;

    public I18nConfig(VcsProperties vcsProperties) {
        this.vcsProperties = vcsProperties;
    }

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setCacheSeconds(3600);
        // Set default fallback.
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }

    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver localeResolver = new SessionLocaleResolver();
        
        // Set default locale - use explicit Locale constructor.
        String defaultLang = vcsProperties.getLang().getDefaultLang();
        switch (defaultLang.toLowerCase()) {
            case "ja":
                localeResolver.setDefaultLocale(new Locale("ja", "JP"));
                break;
            case "en":
            default:
                localeResolver.setDefaultLocale(new Locale("en", "US"));
                break;
        }
        
        return localeResolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor lci = new LocaleChangeInterceptor();
        lci.setParamName("lang"); // URL parameter name.
        return lci;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
