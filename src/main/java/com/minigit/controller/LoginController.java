package com.minigit.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.security.Principal;

/**
 * 登录控制器
 */
@Controller
public class LoginController {

    /**
     * 登录页面
     */
    @GetMapping("/login")
    public String login(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            Model model,
            HttpServletRequest request,
            Principal principal) {
        
        // 如果用户已经登录，重定向到管理页面
        if (principal != null) {
            return "redirect:/admin";
        }
        
        if (error != null) {
            model.addAttribute("error", true);
        }
        
        if (logout != null) {
            model.addAttribute("logout", true);
        }
        
        return "login";
    }
}