package com.apex.config;

import com.apex.common.EmpContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 员工号上下文拦截器配置。
 * 从请求头 X-Emp-No 中提取当前操作员工号，存入 EmpContext（基于 ThreadLocal）。
 * 请求结束后自动清理，天然支持虚拟线程。
 */
@Configuration
public class EmpContextConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new EmpContextInterceptor())
                .addPathPatterns("/api/**");
    }

    private static class EmpContextInterceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            String empNo = request.getHeader("X-Emp-No");
            if (empNo != null && !empNo.isBlank()) {
                EmpContext.setEmpNo(empNo.trim());
            } else {
                EmpContext.setEmpNo("0000000");
            }
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                    Object handler, Exception ex) {
            EmpContext.clear();
        }
    }
}
