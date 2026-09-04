package com.neelastack.controller;

import com.neelastack.repository.BlogPostRepository;
import com.neelastack.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequiredArgsConstructor
public class SeoController {

    private static final DateTimeFormatter LASTMOD_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ProjectRepository projectRepository;
    private final BlogPostRepository blogPostRepository;
    private final com.neelastack.repository.TechStackPageRepository techStackPageRepository;

    @Value("${app.site.base-url}")
    private String baseUrl;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // Static routes: no reliable per-page updatedAt to report, so lastmod is omitted
        // rather than faked with "now" (a false lastmod is worse than none — it teaches
        // crawlers to distrust the signal).
        addUrl(xml, baseUrl + "/", "1.0", "weekly", null);
        addUrl(xml, baseUrl + "/services", "0.9", "monthly", null);
        addUrl(xml, baseUrl + "/portfolio", "0.9", "weekly", null);
        addUrl(xml, baseUrl + "/blog", "0.9", "daily", null);
        addUrl(xml, baseUrl + "/solutions", "0.9", "monthly", null);
        addUrl(xml, baseUrl + "/about", "0.6", "monthly", null);
        addUrl(xml, baseUrl + "/team", "0.6", "monthly", null);
        addUrl(xml, baseUrl + "/estimate", "0.9", "monthly", null);
        addUrl(xml, baseUrl + "/architecture-review", "0.9", "monthly", null);
        addUrl(xml, baseUrl + "/contact", "0.6", "monthly", null);

        // Programmatic SEO silo pages — high-intent, one URL per tech-stack/engagement
        // combination an admin has actually written content for (see V17 migration).
        techStackPageRepository.findByPublishedTrueOrderByDisplayOrderAsc()
                .forEach(t -> addUrl(xml, baseUrl + "/solutions/" + t.getSlug(), "0.8", "monthly",
                        t.getUpdatedAt()));

        projectRepository.findByPublishedTrueOrderByDisplayOrderAsc()
                .forEach(p -> addUrl(xml, baseUrl + "/portfolio/" + p.getSlug(), "0.7", "monthly",
                        p.getUpdatedAt()));

        blogPostRepository.findByPublishedTrueOrderByPublishedAtDesc(org.springframework.data.domain.Pageable.unpaged())
                .forEach(b -> addUrl(xml, baseUrl + "/blog/" + b.getSlug(), "0.7", "monthly",
                        b.getUpdatedAt()));

        xml.append("</urlset>");
        return xml.toString();
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        // robots.txt Disallow only stops crawling — it does NOT deindex a URL that's already
        // linked elsewhere. The actual noindex directive for every one of these routes lives
        // in each Angular route component via SeoService (noindex: true), which is the real
        // control. These Disallow lines are a defense-in-depth measure so crawlers don't spend
        // budget on private/auth/utility paths in the first place.
        return """
                User-agent: *
                Allow: /
                Disallow: /admin
                Disallow: /api/
                Disallow: /dashboard
                Disallow: /login
                Disallow: /register
                Disallow: /forgot-password
                Disallow: /reset-password
                Disallow: /verify-email
                Disallow: /oauth-callback
                Disallow: /quote/

                Sitemap: %s/sitemap.xml
                """.formatted(baseUrl);
    }

    private void addUrl(StringBuilder xml, String loc, String priority, String changefreq,
                         LocalDateTime lastmod) {
        xml.append("  <url>\n")
           .append("    <loc>").append(loc).append("</loc>\n");
        if (lastmod != null) {
            xml.append("    <lastmod>").append(lastmod.format(LASTMOD_FORMAT)).append("</lastmod>\n");
        }
        xml.append("    <changefreq>").append(changefreq).append("</changefreq>\n")
           .append("    <priority>").append(priority).append("</priority>\n")
           .append("  </url>\n");
    }
}
