# Neelastack Booking Engine — audit/fix pass

This archive contains targeted fixes based on the supplied project source and the production errors previously observed.

## Fixed

### 1. Email template formatter
`EmailService.htmlEmail()` had 27 `%s` placeholders but only 22 arguments. This caused `MissingFormatArgumentException` for inquiry, quotation, and executive-report emails. The formatter block now supplies all 27 values.

### 2. Redis serializer mismatch
`CacheConfig` manually activated Jackson default typing on a separate mapper, while the deployed Redis values were being read with an incompatible polymorphic format. The cache now uses `GenericJackson2JsonRedisSerializer.builder().build()` with its self-consistent default configuration, and the cache namespace was bumped from `v4` to `v5` so incompatible `v4` entries are never read by the new serializer.

### 3. iPhone root scrolling
The proven iOS scrolling fix is included:
- `html`: `overflow-x: clip; overflow-y: auto`
- `body`: `overflow-x: clip; overflow-y: visible`

### 4. Booking server validation
Added server-side validation for:
- invalid client timezones;
- unknown dynamic form field keys;
- required dynamic booking fields;
- oversized dynamic field responses;
- invalid SELECT/RADIO options;
- invalid linked inquiry IDs;
- inquiry/client-email mismatch when a booking is linked to an inquiry;
- source/UTM/landing/referrer length limits.

### 5. Availability range hardening
Public availability requests are clamped to the current business date and meeting-type horizon, preventing unnecessarily large historical date scans. The booking lookup range now uses the clamped start date.

### 6. Client-timezone date handling
Booking availability and rescheduling date ranges now derive ISO dates in the client's timezone rather than UTC. Booking management date/time formatting also explicitly uses the stored client timezone.

### 7. Dynamic booking form UX
Dynamic booking inputs now carry the HTML `required` attribute when configured as required.

## Deliberately not changed

The supplied project also contains optional Google Calendar integration and paid-booking scaffolding. These are feature-scope decisions rather than confirmed runtime errors, so they were not rewritten speculatively.

The previously observed Spring Boot `MailHealthIndicator` ~10-second warning is operationally separate from the email formatter bug. This pass does not disable mail health; it should be evaluated against the actual SMTP provider during deployment testing.

## Validation performed in this environment

- Scanned Java text-block `.formatted(...)` blocks: no remaining placeholder/argument-count mismatches were found.
- Confirmed the old custom Redis `activateDefaultTyping` configuration is removed from `CacheConfig`.
- Confirmed the iOS root-scroll rules are present.
- Confirmed booking validation changes are present.
- The archive does not contain installed frontend `node_modules`, and this environment does not have the project's Maven dependency cache, so a full Angular/Spring compile was not claimed as part of this archive-only review. Run the repository's normal CI build before production deployment.
