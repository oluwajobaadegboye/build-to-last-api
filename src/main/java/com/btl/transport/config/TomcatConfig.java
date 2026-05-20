package com.btl.transport.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatSocketCustomizer() {
        // Prevent "Error setting socket options: Invalid argument" logged by NioEndpoint.
        // Some OSes (macOS) reject SO_LINGER on accepted sockets; disabling it is harmless.
        return factory -> factory.addConnectorCustomizers(connector ->
            connector.setProperty("socket.soLingerOn", "false")
        );
    }
}
