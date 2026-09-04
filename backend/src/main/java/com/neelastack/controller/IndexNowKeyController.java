package com.neelastack.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * IndexNow ownership verification: search engines fetch https://{host}/{key}.txt and
 * expect the response body to be exactly the key. Path is matched dynamically (rather
 * than a fixed route) because the key itself IS the path segment per the protocol spec.
 */
@RestController
public class IndexNowKeyController {

    @Value("${app.indexnow.key:}")
    private String indexNowKey;

    @GetMapping(value = "/{requestedKey}.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> keyFile(@PathVariable String requestedKey) {
        if (indexNowKey == null || indexNowKey.isBlank() || !indexNowKey.equals(requestedKey)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(indexNowKey);
    }
}
