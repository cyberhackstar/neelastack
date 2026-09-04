package com.neelastack.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * IndexNow lets a site push "this URL changed" straight to participating search engines
 * instead of waiting for the next crawl. As of this writing that's Bing, Yandex, and
 * Seznam — Google does not consume IndexNow and continues to discover new/changed URLs
 * via sitemap.xml + normal crawling, so this service is a genuine speed win for some
 * engines, not a magic "instant Google indexing" switch. Framing it as anything more
 * than that to a client would be a false claim.
 *
 * Protocol: https://www.indexnow.org/documentation
 * One shared key across engines; the key is proven by serving it back, verbatim, at
 * https://{host}/{key}.txt — see {@link com.neelastack.controller.IndexNowKeyController}.
 */
@Service
@Slf4j
public class IndexNowService {

    private static final String INDEXNOW_ENDPOINT = "https://api.indexnow.org/indexnow";

    private final RestClient restClient = RestClient.builder().build();

    @Value("${app.indexnow.enabled:false}")
    private boolean enabled;

    @Value("${app.indexnow.key:}")
    private String key;

    @Value("${app.site.base-url}")
    private String baseUrl;

    /**
     * Fire-and-forget notification that the page at {@code path} was published or
     * changed. Runs off the request thread (via {@code @Async}) and never throws —
     * a failed ping must never fail the admin's save/publish action.
     *
     * @param path a site-relative path starting with "/", e.g. "/blog/my-post"
     */
    @Async
    public void notifyContentPublished(String path) {
        if (!enabled) {
            return;
        }
        if (key == null || key.isBlank()) {
            log.warn("IndexNow is enabled but app.indexnow.key is not set — skipping ping for {}", path);
            return;
        }

        String url = baseUrl + path;
        try {
            restClient.post()
                    .uri(URI.create(INDEXNOW_ENDPOINT))
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "host", hostOf(baseUrl),
                            "key", key,
                            "keyLocation", baseUrl + "/" + key + ".txt",
                            "urlList", List.of(url)
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info("IndexNow: submitted {}", url);
        } catch (Exception ex) {
            // Best-effort only — IndexNow being unreachable is not a reason to disrupt
            // publishing, and it isn't the primary indexing path (the sitemap is).
            log.warn("IndexNow submission failed for {}: {}", url, ex.getMessage());
        }
    }

    private static String hostOf(String url) {
        return URI.create(url).getHost();
    }
}
