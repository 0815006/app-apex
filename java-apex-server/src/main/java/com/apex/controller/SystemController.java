package com.apex.controller;

import com.apex.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    @GetMapping("/info")
    public Result<SystemInfoDTO> info(HttpServletRequest request) {
        String loginIp = request.getHeader("X-Forwarded-For");
        if (loginIp == null || loginIp.isBlank()) {
            loginIp = request.getHeader("X-Real-IP");
        }
        if (loginIp == null || loginIp.isBlank()) {
            loginIp = request.getRemoteAddr();
        }
        return Result.success(new SystemInfoDTO(loginIp));
    }

    public record SystemInfoDTO(String loginIp) {}
}
