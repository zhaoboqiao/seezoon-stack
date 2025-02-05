package com.seezoon.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

import com.seezoon.admin.modules.sys.security.LoginSecurityProperties;

/**
 * 程序入口
 *
 * @author hdf
 */
@SpringBootApplication
@EnableConfigurationProperties({LoginSecurityProperties.class})
@EnableAsync
public class AdminMain {

    public static void main(String[] args) {
        SpringApplication.run(AdminMain.class, args);
    }
}
