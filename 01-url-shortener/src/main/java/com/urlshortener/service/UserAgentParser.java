package com.urlshortener.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to parse User-Agent strings and extract device/browser information
 */
@Service
public class UserAgentParser {

    private static final Pattern BROWSER_PATTERN = Pattern.compile(
            "(Chrome|Firefox|Safari|Edge|Opera|MSIE|Trident)[/\\s]([\\d.]+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern OS_PATTERN = Pattern.compile(
            "(Windows NT [\\d.]+|Mac OS X [\\d._]+|Linux|Android [\\d.]+|iOS [\\d._]+|iPhone OS [\\d._]+)",
            Pattern.CASE_INSENSITIVE);

    public String getBrowser(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown";
        }

        Matcher matcher = BROWSER_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            String browser = matcher.group(1);
            // Handle IE special case
            if ("Trident".equalsIgnoreCase(browser)) {
                return "Internet Explorer";
            }
            if ("MSIE".equalsIgnoreCase(browser)) {
                return "Internet Explorer";
            }
            return browser;
        }

        return "Unknown";
    }

    public String getOperatingSystem(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown";
        }

        Matcher matcher = OS_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            String os = matcher.group(1);

            if (os.contains("Windows NT")) {
                return mapWindowsVersion(os);
            }
            if (os.contains("Mac OS X")) {
                return "macOS";
            }
            if (os.contains("Android")) {
                return "Android";
            }
            if (os.contains("iOS") || os.contains("iPhone OS")) {
                return "iOS";
            }
            if (os.contains("Linux")) {
                return "Linux";
            }

            return os;
        }

        return "Unknown";
    }

    public String getDeviceType(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown";
        }

        String lowerUA = userAgent.toLowerCase();

        if (lowerUA.contains("mobile") || lowerUA.contains("android") && lowerUA.contains("mobile")) {
            return "Mobile";
        }
        if (lowerUA.contains("tablet") || lowerUA.contains("ipad")) {
            return "Tablet";
        }
        if (lowerUA.contains("bot") || lowerUA.contains("crawler") || lowerUA.contains("spider")) {
            return "Bot";
        }

        return "Desktop";
    }

    public String getClientIp(HttpServletRequest request) {
        String[] headerNames = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For may contain multiple IPs, take the first one
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        return request.getRemoteAddr();
    }

    private String mapWindowsVersion(String os) {
        if (os.contains("10.0") || os.contains("11.0")) {
            return "Windows 10/11";
        }
        if (os.contains("6.3")) {
            return "Windows 8.1";
        }
        if (os.contains("6.2")) {
            return "Windows 8";
        }
        if (os.contains("6.1")) {
            return "Windows 7";
        }
        return "Windows";
    }
}
