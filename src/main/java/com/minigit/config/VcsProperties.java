package com.minigit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * VCS配置属性
 */
@Component
@ConfigurationProperties(prefix = "vcs")
public class VcsProperties {

    /**
     * 存储配置
     */
    private Storage storage = new Storage();

    /**
     * 认证配置
     */
    private Auth auth = new Auth();

    /**
     * 语言配置
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
         * 仓库存储目录，默认为 ./data/repos
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
         * 默认用户名
         */
        private String user = "admin";

        /**
         * 默认密码
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
         * 默认语言，支持 en/zh/ja
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