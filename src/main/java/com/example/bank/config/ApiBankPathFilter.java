package com.example.bank.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiBankPathFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();
        if (path.startsWith("/api-bank")) {
            String newPath = path.substring(9); // Strip "/api-bank"
            if (newPath.isEmpty()) {
                newPath = "/";
            }
            final String finalPath = newPath;
            HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(httpRequest) {
                @Override
                public String getRequestURI() {
                    return finalPath;
                }
                @Override
                public StringBuffer getRequestURL() {
                    StringBuffer sb = new StringBuffer();
                    sb.append(httpRequest.getScheme())
                      .append("://")
                      .append(httpRequest.getServerName());
                    int port = httpRequest.getServerPort();
                    if (port > 0 && port != 80 && port != 443) {
                        sb.append(":").append(port);
                    }
                    sb.append(finalPath);
                    return sb;
                }
                @Override
                public String getServletPath() {
                    return finalPath;
                }
                @Override
                public String getContextPath() {
                    return "";
                }
            };
            chain.doFilter(wrappedRequest, response);
        } else {
            chain.doFilter(request, response);
        }
    }
}
