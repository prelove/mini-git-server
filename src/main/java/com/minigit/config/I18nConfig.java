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
 * 国际化配置
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
        // 设置默认fallback
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }

    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver localeResolver = new SessionLocaleResolver();
        
        // 设置默认语言 - 使用明确的Locale构造器
        String defaultLang = vcsProperties.getLang().getDefaultLang();
        switch (defaultLang.toLowerCase()) {
            case "zh":
                localeResolver.setDefaultLocale(new Locale("zh", "CN"));
                break;
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
        lci.setParamName("lang"); // URL参数名
        return lci;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}