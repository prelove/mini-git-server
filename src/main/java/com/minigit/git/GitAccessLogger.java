package com.minigit.git;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;

/**
 * Git访问日志记录器
 */
@Component
public class GitAccessLogger {

    private static final Logger logger = LoggerFactory.getLogger("com.minigit.git.access");

    /**
     * 记录Git操作
     */
    public void logGitOperation(HttpServletRequest request, String repository, String operation, String user, boolean success, long duration) {
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        
        String logMessage = String.format(
            "OPERATION=%s REPO=%s USER=%s IP=%s SUCCESS=%s DURATION=%dms USER_AGENT=%s",
            operation, repository, user, clientIp, success, duration, userAgent
        );
        
        if (success) {
            logger.info(logMessage);
        } else {
            logger.warn(logMessage);
        }
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}