package com.btl.transport.admin;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "btl.admin")
@Getter
@Setter
public class AdminProperties {

    private Jwt jwt = new Jwt();
    private List<AdminUser> users = new ArrayList<>();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private int expiryHours = 12;
    }

    @Getter
    @Setter
    public static class AdminUser {
        private String username;
        private String passwordHash;
    }
}
