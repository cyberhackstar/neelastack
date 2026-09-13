# Final CSP fix — 2026-09-13

## What changed

The Playwright suite was green (18/18), but Razorpay-related browser runs still emitted `Executing inline event handler violates ... script-src` console errors.

The application source contains no inline HTML event handlers. The remaining violation can be produced by third-party checkout/injected markup. The CSP is therefore refined to allow inline **event attributes only** via `script-src-attr 'unsafe-inline'`, while keeping `script-src` nonce-based and without enabling `unsafe-inline` for executable script elements.

This keeps the main JavaScript policy strict and removes the specific event-handler console violation observed during the checkout journey.

## Validation

- `script-src` remains nonce-based.
- `script-src-attr` is limited to event attributes.
- No `unsafe-inline` was added to `script-src`.
- Nginx remains free of a duplicate CSP header.
