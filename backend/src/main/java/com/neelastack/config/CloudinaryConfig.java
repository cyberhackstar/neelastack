package com.neelastack.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class CloudinaryConfig {

    @Value("${app.cloudinary.cloud-name}")
    private String cloudName;

    @Value("${app.cloudinary.api-key}")
    private String apiKey;

    @Value("${app.cloudinary.api-secret}")
    private String apiSecret;

    @Value("${app.cloudinary.auth-token-key:}")
    private String authTokenKey;

    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }

    /**
     * FileStorageService silently degrades to permanently-reusable signed URLs when
     * app.cloudinary.auth-token-key is blank (see its own warning log). That's a reasonable
     * default for local/dev, but shipping it to production by accident is a real client-file
     * exposure risk on a client portal. Failing fast at boot — rather than only logging a
     * warning that's easy to miss in production log volume — makes the missing key a deploy
     * blocker instead of a silent security gap.
     */
    @Bean
    @Profile("prod")
    public ApplicationRunner requireCloudinaryAuthTokenKeyInProduction() {
        return (ApplicationArguments args) -> {
            if (authTokenKey == null || authTokenKey.isBlank()) {
                throw new IllegalStateException(
                        "app.cloudinary.auth-token-key (CLOUDINARY_AUTH_TOKEN_KEY) is required in the " +
                        "prod profile. Without it, signed file URLs never expire and remain permanently " +
                        "reusable by anyone who obtains one. Enable \"Token-based authentication\" in the " +
                        "Cloudinary console and set the env var before deploying."
                );
            }
        };
    }
}
