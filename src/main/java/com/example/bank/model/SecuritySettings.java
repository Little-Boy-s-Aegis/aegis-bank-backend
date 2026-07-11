package com.example.bank.model;

public class SecuritySettings {
    private boolean sqliEnabled = false;
    private boolean xssEnabled = false;
    private boolean idorEnabled = false;
    private boolean paramTamperingEnabled = false;
    private boolean bruteForceEnabled = false;

    private static final SecuritySettings instance = new SecuritySettings();

    private SecuritySettings() {}

    public static SecuritySettings getInstance() {
        return instance;
    }

    public boolean isProduction() {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        if (url != null && url.contains(".rds.amazonaws.com")) {
            return true;
        }
        String env = System.getenv("AEGIS_ENVIRONMENT");
        if (env != null && (env.equalsIgnoreCase("production") || env.equalsIgnoreCase("prod"))) {
            return true;
        }
        return false;
    }

    public boolean isSqliEnabled() {
        if (isProduction()) return false;
        return sqliEnabled;
    }

    public void setSqliEnabled(boolean sqliEnabled) {
        this.sqliEnabled = sqliEnabled;
    }

    public boolean isXssEnabled() {
        if (isProduction()) return false;
        return xssEnabled;
    }

    public void setXssEnabled(boolean xssEnabled) {
        this.xssEnabled = xssEnabled;
    }

    public boolean isIdorEnabled() {
        if (isProduction()) return false;
        return idorEnabled;
    }

    public void setIdorEnabled(boolean idorEnabled) {
        this.idorEnabled = idorEnabled;
    }

    public boolean isParamTamperingEnabled() {
        if (isProduction()) return false;
        return paramTamperingEnabled;
    }

    public void setParamTamperingEnabled(boolean paramTamperingEnabled) {
        this.paramTamperingEnabled = paramTamperingEnabled;
    }

    public boolean isBruteForceEnabled() {
        if (isProduction()) return false;
        return bruteForceEnabled;
    }

    public void setBruteForceEnabled(boolean bruteForceEnabled) {
        this.bruteForceEnabled = bruteForceEnabled;
    }
}
