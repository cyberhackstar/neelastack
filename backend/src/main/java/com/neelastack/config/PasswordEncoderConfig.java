package com.neelastack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Split out from SecurityConfig deliberately: OAuth2LoginSuccessHandler needs
 * a PasswordEncoder (to hash a random, unusable password for Google-signup
 * users), and SecurityConfig needs OAuth2LoginSuccessHandler injected into
 * its constructor. If PasswordEncoder were a @Bean method on SecurityConfig
 * itself, Spring would need to fully construct SecurityConfig to get the
 * encoder, but constructing SecurityConfig requires OAuth2LoginSuccessHandler
 * first — a circular dependency. Keeping it in its own tiny config class with
 * no dependencies breaks the cycle.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
