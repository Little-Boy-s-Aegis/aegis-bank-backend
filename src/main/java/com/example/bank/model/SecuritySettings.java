package com.example.bank.model;

public class SecuritySettings {
    private boolean sqliEnabled = true;
    private boolean xssEnabled = true;
    private boolean idorEnabled = true;
    private boolean paramTamperingEnabled = true;
    private boolean bruteForceEnabled = true;

    private static final SecuritySettings instance = new SecuritySettings();

    private SecuritySettings() {}

    public static SecuritySettings getInstance() {
        return instance;
    }

    public boolean isSqliEnabled() {
        return sqliEnabled;
    }

    public void setSqliEnabled(boolean sqliEnabled) {
        this.sqliEnabled = sqliEnabled;
    }

    public boolean isXssEnabled() {
        return xssEnabled;
    }

    public void setXssEnabled(boolean xssEnabled) {
        this.xssEnabled = xssEnabled;
    }

    public boolean isIdorEnabled() {
        return idorEnabled;
    }

    public void setIdorEnabled(boolean idorEnabled) {
        this.idorEnabled = idorEnabled;
    }

    public boolean isParamTamperingEnabled() {
        return paramTamperingEnabled;
    }

    public void setParamTamperingEnabled(boolean paramTamperingEnabled) {
        this.paramTamperingEnabled = paramTamperingEnabled;
    }

    public boolean isBruteForceEnabled() {
        return bruteForceEnabled;
    }

    public void setBruteForceEnabled(boolean bruteForceEnabled) {
        this.bruteForceEnabled = bruteForceEnabled;
    }
}
