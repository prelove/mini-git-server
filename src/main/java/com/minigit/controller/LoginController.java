package com.minigit.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.security.Principal;

/**
 * Login controller.
 */
@Controller
public class LoginController {

    /**
     * Login page.
     */
    @GetMapping("/login")
    public String login(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            Model model,
            HttpServletRequest request,
            Principal principal) {
        
        // If the user is already authenticated, redirect to admin.
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
