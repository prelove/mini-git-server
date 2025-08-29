package com.minigit.controller;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 语言调试控制器
 */
@RestController
@RequestMapping("/debug")
public class LanguageDebugController {

    private final MessageSource messageSource;

    public LanguageDebugController(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * 调试当前语言设置
     */
    @GetMapping("/locale")
    public Map<String, Object> debugLocale(HttpServletRequest request) {
        Map<String, Object> debug = new HashMap<>();
        
        Locale currentLocale = LocaleContextHolder.getLocale();
        
        debug.put("currentLocale", currentLocale.toString());
        debug.put("language", currentLocale.getLanguage());
        debug.put("country", currentLocale.getCountry());
        debug.put("displayName", currentLocale.getDisplayName());
        
        // 测试消息
        debug.put("titleMessage", messageSource.getMessage("ui.title", null, "DEFAULT", currentLocale));
        debug.put("welcomeMessage", messageSource.getMessage("ui.welcome", null, "DEFAULT", currentLocale));
        
        // 请求头信息
        debug.put("acceptLanguage", request.getHeader("Accept-Language"));
        debug.put("langParam", request.getParameter("lang"));
        
        return debug;
    }
}