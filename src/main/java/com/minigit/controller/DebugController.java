package com.minigit.controller;

import com.minigit.config.VcsProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 调试控制器 - 仅用于开发调试
 */
@RestController
@RequestMapping("/debug")
public class DebugController {

    private final VcsProperties vcsProperties;
    private final PasswordEncoder passwordEncoder;

    public DebugController(VcsProperties vcsProperties, PasswordEncoder passwordEncoder) {
        this.vcsProperties = vcsProperties;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 显示当前配置信息
     */
    @GetMapping("/config")
    public Map<String, Object> showConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("username", vcsProperties.getAuth().getUser());
        config.put("passwordHash", passwordEncoder.encode(vcsProperties.getAuth().getPass()));
        config.put("storageDir", vcsProperties.getStorage().getDir());
        config.put("defaultLang", vcsProperties.getLang().getDefaultLang());
        return config;
    }
}