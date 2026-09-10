package com.neelastack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neelastack.entity.Booking;
import com.neelastack.entity.CalendarConnection;
import com.neelastack.repository.CalendarConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * OPTIONAL feature, off by default (app.google-calendar.enabled=false). Uses the
 * Google Calendar API's free tier only -- no paid quota is required for a single
 * business calendar's normal booking volume. When disabled or unconfigured, every
 * public method here degrades to a harmless no-op so the rest of the booking engine
 * behaves identically to a deployment that never enables this.
 *
 * Requires (documented in README-BOOKING-ENGINE.md, not automated here): a Google
 * Cloud OAuth 2.0 client with the Calendar API enabled and
 * https://www.googleapis.com/auth/calendar scope, plus a dedicated redirect URI for
 * GET /api/v1/admin/booking/calendar/callback registered in that OAuth client.
 * Reuses the same GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRET already configured for
 * Google sign-in (spring.security.oauth2.client.registration.google) -- same Google
 * Cloud project, a second consent screen with a wider scope.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleCalendarService {

    private final CalendarConnectionRepository calendarConnectionRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.google-calendar.enabled:false}")
    private boolean enabled;

    @Value("${GOOGLE_CLIENT_ID:}")
    private String clientId;

    @Value("${GOOGLE_CLIENT_SECRET:}")
    private String clientSecret;

    @Value("${app.google-calendar.redirect-uri:}")
    private String redirectUri;

    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String CALENDAR_API = "https://www.googleapis.com/calendar/v3";

    private final RestClient restClient = RestClient.create();

    /** Short-lived, single-use state tokens for the OAuth connect round-trip. The Google
     *  redirect back to our callback is a plain browser navigation and cannot carry our
     *  JWT bearer token, so authorization for "may this caller complete the connect flow"
     *  is proven by possession of one of these tokens (generated only from an already-
     *  authenticated ROLE_ADMIN request) instead of a Spring Security check on the
     *  callback route itself. In-memory is fine here: single-tenant feature, low volume,
     *  and a token is only ever needed within the few seconds of one consent redirect. */
    private final java.util.Map<String, Instant> pendingStates = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    public record Interval(Instant start, Instant end) {}

    public boolean isEnabled() {
        return enabled && !clientId.isBlank() && !clientSecret.isBlank();
    }

    public boolean overlapsAnyBusyInterval(List<Interval> busy, Instant start, Instant end) {
        return busy.stream().anyMatch(b -> start.isBefore(b.end()) && end.isAfter(b.start()));
    }

    // --- OAuth connect flow ----------------------------------------------------------

    /** Called from an authenticated (ROLE_ADMIN) request only. */
    public String buildAuthorizationUrl() {
        pendingStates.entrySet().removeIf(e -> e.getValue().isBefore(Instant.now()));
        String state = UUID.randomUUID().toString();
        pendingStates.put(state, Instant.now().plus(STATE_TTL));
        return AUTH_ENDPOINT
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&access_type=offline"
                + "&prompt=consent"
                + "&scope=" + "https://www.googleapis.com/auth/calendar"
                + "&state=" + state;
    }

    /** Called from the public (unauthenticated) callback route — see the class javadoc and
     *  SecurityConfig for why this route can't require normal auth. */
    public void verifyState(String state) {
        Instant expiry = pendingStates.remove(state);
        if (expiry == null || expiry.isBefore(Instant.now())) {
            throw new IllegalStateException("This calendar-connect link has expired or was already used. Please reconnect from the admin dashboard.");
        }
    }

    @Transactional
    public void handleOAuthCallback(String code) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("code", code);
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);
        body.put("redirect_uri", redirectUri);
        body.put("grant_type", "authorization_code");

        JsonNode response = restClient.post().uri(TOKEN_ENDPOINT)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        String accessToken = response.get("access_token").asText();
        String refreshToken = response.has("refresh_token") ? response.get("refresh_token").asText() : null;
        int expiresIn = response.get("expires_in").asInt();

        if (refreshToken == null) {
            throw new IllegalStateException(
                    "Google did not return a refresh token. Revoke access at " +
                    "https://myaccount.google.com/permissions and reconnect with prompt=consent.");
        }

        CalendarConnection connection = calendarConnectionRepository.findFirstByIsActiveTrueOrderByCreatedAtDesc()
                .orElseGet(() -> CalendarConnection.builder().build());
        connection.setProvider("GOOGLE");
        connection.setAccessToken(accessToken);
        connection.setRefreshToken(refreshToken);
        connection.setTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
        connection.setIsActive(true);
        calendarConnectionRepository.save(connection);
    }

    @Transactional
    public void disconnect() {
        calendarConnectionRepository.findFirstByIsActiveTrueOrderByCreatedAtDesc()
                .ifPresent(c -> { c.setIsActive(false); calendarConnectionRepository.save(c); });
    }

    public boolean isConnected() {
        return calendarConnectionRepository.findFirstByIsActiveTrueOrderByCreatedAtDesc().isPresent();
    }

    private Optional<String> freshAccessToken() {
        Optional<CalendarConnection> maybeConn = calendarConnectionRepository.findFirstByIsActiveTrueOrderByCreatedAtDesc();
        if (maybeConn.isEmpty()) {
            return Optional.empty();
        }
        CalendarConnection connection = maybeConn.get();
        if (connection.getTokenExpiresAt().isAfter(LocalDateTime.now().plusMinutes(2))) {
            return Optional.of(connection.getAccessToken());
        }
        return refreshAccessToken(connection);
    }

    private Optional<String> refreshAccessToken(CalendarConnection connection) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("client_id", clientId);
            body.put("client_secret", clientSecret);
            body.put("refresh_token", connection.getRefreshToken());
            body.put("grant_type", "refresh_token");

            JsonNode response = restClient.post().uri(TOKEN_ENDPOINT).body(body).retrieve().body(JsonNode.class);
            String accessToken = response.get("access_token").asText();
            int expiresIn = response.get("expires_in").asInt();

            connection.setAccessToken(accessToken);
            connection.setTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
            calendarConnectionRepository.save(connection);
            return Optional.of(accessToken);
        } catch (Exception e) {
            log.error("Failed to refresh Google Calendar access token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- Reads: busy blocks ------------------------------------------------------------

    /** Never throws -- any failure (not connected, token expired and unrefreshable, API
     *  error, network issue) degrades to "no known busy blocks" rather than breaking the
     *  public availability endpoint. */
    public List<Interval> listBusyIntervalsSafely(Instant from, Instant to) {
        try {
            return listBusyIntervals(from, to);
        } catch (Exception e) {
            log.warn("Google Calendar free/busy lookup failed, proceeding without it: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Interval> listBusyIntervals(Instant from, Instant to) {
        Optional<String> token = freshAccessToken();
        if (token.isEmpty()) {
            return List.of();
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("timeMin", from.toString());
        body.put("timeMax", to.toString());
        body.putArray("items").addObject().put("id", "primary");

        JsonNode response = restClient.post().uri(CALENDAR_API + "/freeBusy")
                .header("Authorization", "Bearer " + token.get())
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        List<Interval> busy = new ArrayList<>();
        JsonNode busyArray = response.path("calendars").path("primary").path("busy");
        if (busyArray.isArray()) {
            for (JsonNode slot : busyArray) {
                busy.add(new Interval(Instant.parse(slot.get("start").asText()), Instant.parse(slot.get("end").asText())));
            }
        }
        return busy;
    }

    // --- Writes: create/cancel an event for a confirmed booking -----------------------

    /** Best-effort: a calendar-sync failure never blocks or rolls back the booking itself. */
    public Optional<String[]> createEventSafely(Booking booking, String meetingTypeName) {
        try {
            return createEvent(booking, meetingTypeName);
        } catch (Exception e) {
            log.error("Failed to create Google Calendar event for booking {}: {}", booking.getBookingNumber(), e.getMessage());
            return Optional.empty();
        }
    }

    /** Returns [eventId, meetLink] on success. */
    private Optional<String[]> createEvent(Booking booking, String meetingTypeName) {
        Optional<String> token = freshAccessToken();
        if (token.isEmpty()) {
            return Optional.empty();
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("summary", meetingTypeName + " with " + booking.getClientName());
        body.put("description", "Booking " + booking.getBookingNumber() + " via neelastack.com");

        ObjectNode start = body.putObject("start");
        start.put("dateTime", booking.getStartAt().toString());
        ObjectNode end = body.putObject("end");
        end.put("dateTime", booking.getEndAt().toString());

        body.putArray("attendees").addObject().put("email", booking.getClientEmail());

        ObjectNode conferenceData = body.putObject("conferenceData");
        ObjectNode createRequest = conferenceData.putObject("createRequest");
        createRequest.put("requestId", UUID.randomUUID().toString());

        JsonNode response = restClient.post()
                .uri(CALENDAR_API + "/calendars/primary/events?conferenceDataVersion=1&sendUpdates=all")
                .header("Authorization", "Bearer " + token.get())
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        String eventId = response.path("id").asText(null);
        String meetLink = response.path("hangoutLink").asText(null);
        if (eventId == null) {
            return Optional.empty();
        }
        return Optional.of(new String[]{eventId, meetLink});
    }

    public void deleteEventSafely(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        try {
            Optional<String> token = freshAccessToken();
            if (token.isEmpty()) {
                return;
            }
            restClient.delete()
                    .uri(CALENDAR_API + "/calendars/primary/events/" + eventId + "?sendUpdates=all")
                    .header("Authorization", "Bearer " + token.get())
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to delete Google Calendar event {}: {}", eventId, e.getMessage());
        }
    }
}
