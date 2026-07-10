package com.example.bank.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class IpBlockFilter extends OncePerRequestFilter {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS banned_ips (" +
                "  ip_address VARCHAR(64) PRIMARY KEY, " +
                "  banned_at TIMESTAMP, " +
                "  banned_by VARCHAR(100), " +
                "  status VARCHAR(20) DEFAULT 'active', " +
                "  reason VARCHAR(255)" +
                ")"
            );
            try {
                jdbcTemplate.execute("ALTER TABLE banned_ips ALTER COLUMN ip_address TYPE VARCHAR(64)");
            } catch (Exception postgresAlterFailed) {
                jdbcTemplate.execute("ALTER TABLE banned_ips ALTER COLUMN ip_address VARCHAR(64)");
            }
        } catch (Exception e) {
            logger.warn("Failed to ensure banned_ips table exists: " + e.getMessage());
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        
        List<String> clientIps = getClientIpCandidates(request);
        
        boolean isBanned = false;
        String matchedIp = "";
        String matchedRule = "";
        try {
            List<String> activeBanRules = jdbcTemplate.queryForList(
                "SELECT ip_address FROM banned_ips WHERE status = 'active'",
                String.class
            );
            for (String clientIp : clientIps) {
                for (String rule : activeBanRules) {
                    if (ipMatches(rule, clientIp)) {
                        isBanned = true;
                        matchedIp = clientIp;
                        matchedRule = rule;
                        break;
                    }
                }
                if (isBanned) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to check banned IP status: " + e.getMessage());
        }

        if (isBanned) {
            logger.warn("Blocking request from banned IP: " + matchedIp + " (rule: " + matchedRule + ")");
            revokeRequestAuth(request, response);
            writeBannedResponse(response, matchedIp, matchedRule);
            return;
        }

        chain.doFilter(request, response);
    }

    private void revokeRequestAuth(HttpServletRequest request, HttpServletResponse response) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            jwtTokenUtil.blacklistToken(token);
            try {
                jwtTokenUtil.blacklistAllTokensForUser(jwtTokenUtil.getUsernameFromToken(token));
            } catch (Exception ignored) {
                // The presented token is still blacklisted even when claims cannot be parsed.
            }
        }
        SecurityContextHolder.clearContext();
        expireCookie(response, "token");
        expireCookie(response, "user");
        expireCookie(response, "session_token");
    }

    private void expireCookie(HttpServletResponse response, String name) {
        response.addHeader("Set-Cookie", name + "=; Path=/; Max-Age=0; SameSite=Strict; Secure");
    }

    private void writeBannedResponse(HttpServletResponse response, String matchedIp, String matchedRule) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store, no-cache, max-age=0, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Aegis-IP-Banned", "true");
        response.setHeader("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\"");
        response.getWriter().write("""
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Access Revoked | Aegis Bank</title>
              <style>
                :root { color-scheme: dark; font-family: Inter, Arial, sans-serif; }
                body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #080b12; color: #f8fafc; }
                main { width: min(560px, calc(100vw - 32px)); border: 1px solid rgba(244, 63, 94, .35); background: #111827; padding: 32px; box-shadow: 0 24px 80px rgba(0,0,0,.35); }
                h1 { margin: 0 0 12px; font-size: 28px; }
                p { margin: 8px 0; color: #cbd5e1; line-height: 1.55; }
                code { display: inline-block; margin-top: 14px; padding: 8px 10px; background: rgba(244, 63, 94, .12); color: #fecdd3; }
              </style>
            </head>
            <body>
              <main>
                <h1>Access revoked</h1>
                <p>Your IP address has been blocked by the Aegis security policy.</p>
                <p>All browser-side authentication state for this origin has been cleared and any presented token has been revoked.</p>
                <code>403 IP_BANNED</code>
              </main>
            </body>
            </html>
            """);
    }

    private List<String> getClientIpCandidates(HttpServletRequest request) {
        Set<String> ips = new LinkedHashSet<>();
        addIpCandidate(ips, request.getHeader("X-Real-IP"));
        addIpCandidate(ips, request.getHeader("CF-Connecting-IP"));

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            for (String part : forwardedFor.split(",")) {
                addIpCandidate(ips, part);
            }
        }

        addIpCandidate(ips, request.getRemoteAddr());
        return new ArrayList<>(ips);
    }

    private void addIpCandidate(Set<String> ips, String value) {
        String normalized = normalizeIp(value);
        if (normalized != null) {
            ips.add(normalized);
        }
    }

    private String normalizeIp(String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.isEmpty() || "unknown".equalsIgnoreCase(candidate)) {
            return null;
        }
        if (candidate.startsWith("[") && candidate.contains("]")) {
            candidate = candidate.substring(1, candidate.indexOf("]"));
        } else if (candidate.indexOf(':') == candidate.lastIndexOf(':') && candidate.contains(":")) {
            candidate = candidate.substring(0, candidate.indexOf(':'));
        }

        try {
            return InetAddress.getByName(candidate).getHostAddress();
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean ipMatches(String rawRule, String rawClientIp) {
        String rule = normalizeBanRule(rawRule);
        String clientIp = normalizeIp(rawClientIp);
        if (rule == null || clientIp == null) {
            return false;
        }

        if (rule.contains("/")) {
            return cidrContains(rule, clientIp);
        }

        String normalizedRuleIp = normalizeIp(rule);
        return normalizedRuleIp != null && normalizedRuleIp.equals(clientIp);
    }

    private String normalizeBanRule(String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.toLowerCase().startsWith("ip ")) {
            candidate = candidate.substring(3).trim();
        }
        return candidate.isEmpty() ? null : candidate;
    }

    private boolean cidrContains(String cidr, String rawClientIp) {
        try {
            String[] parts = cidr.split("/", 2);
            if (parts.length != 2) {
                return false;
            }

            InetAddress network = InetAddress.getByName(parts[0].trim());
            InetAddress client = InetAddress.getByName(rawClientIp.trim());
            byte[] networkBytes = network.getAddress();
            byte[] clientBytes = client.getAddress();
            if (networkBytes.length != clientBytes.length) {
                return false;
            }

            int prefixLength = Integer.parseInt(parts[1].trim());
            int totalBits = networkBytes.length * 8;
            if (prefixLength < 0 || prefixLength > totalBits) {
                return false;
            }

            BigInteger networkInt = new BigInteger(1, networkBytes);
            BigInteger clientInt = new BigInteger(1, clientBytes);
            int shift = totalBits - prefixLength;
            return networkInt.shiftRight(shift).equals(clientInt.shiftRight(shift));
        } catch (Exception ignored) {
            return false;
        }
    }
}
