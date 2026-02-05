package com.minigit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * VCS configuration properties.
 */
@Component
@ConfigurationProperties(prefix = "vcs")
public class VcsProperties {

    /**
     * Storage configuration.
     */
    private Storage storage = new Storage();

    /**
     * Authentication configuration.
     */
    private Auth auth = new Auth();

    /**
     * Locale configuration.
     */
    private Lang lang = new Lang();

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public Lang getLang() {
        return lang;
    }

    public void setLang(Lang lang) {
        this.lang = lang;
    }

    public static class Storage {
        /**
         * Repository storage directory, defaults to ./data/repos.
         */
        private String dir = "./data/repos";

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }
    }

    public static class Auth {
        /**
         * Default username.
         */
        private String user = "admin";

        /**
         * Default password.
         */
        private String pass = "admin123";

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getPass() {
            return pass;
        }

        public void setPass(String pass) {
            this.pass = pass;
        }
    }

    public static class Lang {
        /**
         * Default language, supports en/ja.
         */
        private String defaultLang = "en";

        public String getDefaultLang() {
            return defaultLang;
        }

        public void setDefaultLang(String defaultLang) {
            this.defaultLang = defaultLang;
        }
    }
}
