# CHANGES — Neelastack theme overhaul (bone & ink + bronze accent)

Scope: **theme only**. No routes, components' logic, backend, migrations, or
content were touched — every change below is a color/token/toggle change in
the Angular frontend.

## What changed

### 1. Core palette — `frontend/src/styles.scss`
The entire site already read its colors from a small set of CSS custom
properties defined once in `:root` (this was the key thing that made a full
re-theme possible without rewriting 40+ component files). That block was
replaced with your new palette, split into two theme blocks:

- `:root, [data-theme="dark"]` — the new default dark theme: ink background
  (`#0C0C0D`), warm bone text (`#F2EFE8`), bronze accent (`#C9A227`) replacing
  the old amber, and a muted teal (`#5C8A82`) replacing the old bright teal
  for the secondary "engineering" accent.
- `[data-theme="light"]` — new: bone background (`#FAF9F6`), ink text
  (`#111110`), the same bronze accent darkened for contrast (`#9C7A1B`), from
  your uploaded `neelastack-theme.css` reference.
- Added RGB-triplet variables (`--color-amber-rgb`, `--color-teal-rgb`,
  `--color-bg-rgb`, etc.) so every `rgba(...)` glow/overlay effect in the
  codebase tracks the active theme instead of being locked to the old colors.
- Added `--color-danger` / `--color-success` / `--color-info` / `--color-ink`
  tokens so status colors (errors, success badges, info links, text-on-accent)
  are theme-aware and readable in both modes — the light theme in particular
  needed darker reds/greens than the dark theme to stay readable on a bone
  background.
- Added a scoped `transition` on background/border/color for the handful of
  large surfaces (body, cards, navbar, auth panels) so switching themes
  cross-fades instead of snapping, with a `prefers-reduced-motion` bail-out.

### 2. Every hardcoded color in components, replaced with theme variables
Swept all `.scss` and `.html` files under `frontend/src/app` for hex/rgba
colors that bypassed the variable system and pointed them at the new tokens:
old amber/teal hex duplicated in the architecture diagram SVG, status colors
(`#ff6b6b` danger, `#3fb950` success, `#58a6ff` info, `#8B949E` muted) used
across the admin dashboard/inquiries/content pages, the navbar's translucent
background, and a few glow/shadow effects in the browser-mockup and services
components.

**Deliberately left untouched** (not part of the site's own theme):
- Google's official brand colors in the Google Sign-In button SVG.
- The white background behind the MFA QR code (`admin-security` page) — QR
  codes need a plain white quiet zone to stay scannable regardless of theme.
- Razorpay's checkout widget `theme.color` config — updated to the new
  bronze so the payment popup matches, but it's a third-party overlay with
  its own light background, not part of our CSS.

### 3. Dark/light toggle
- **New:** `frontend/src/app/core/services/theme.service.ts` — signal-based
  service that reads the saved/OS-preferred theme, applies it via
  `data-theme` on `<html>`, and persists changes to `localStorage`
  (`neelastack_theme`). SSR-safe (guards all browser-only APIs).
- **New:** `frontend/src/app/shared/components/theme-toggle/` — an animated
  pill switch with sun/moon icons (`theme-toggle.component.ts/html`); styles
  live globally in `styles.scss` so it can be dropped anywhere.
- Wired into `navbar.component.html`/`.ts`/`.scss`: one instance always
  visible in the desktop bar, one inside the mobile dropdown panel.
- **`index.html`:** added a small inline script in `<head>` that applies the
  saved/OS theme to `<html>` before any CSS loads, so there's no flash of
  the wrong theme on first paint. Updated the `theme-color` meta tag to the
  new ink color.

### 4. Everything else
Typography, layout, spacing, radii, component structure, routing, and all
non-visual code are unchanged. The site was already responsive (mobile nav,
`.container` breakpoints, the "mobile safety net" rules in `styles.scss`) —
that wasn't touched, and the new toggle was built to fit inside the existing
responsive nav rather than adding a new breakpoint.

## Files touched
```
frontend/src/styles.scss                                            (rewritten palette + toggle styles)
frontend/src/index.html                                              (anti-flash script, theme-color meta)
frontend/src/app/core/services/theme.service.ts                      (new)
frontend/src/app/shared/components/theme-toggle/*                    (new)
frontend/src/app/shared/components/navbar/navbar.component.{ts,html,scss}
frontend/src/app/shared/components/architecture-diagram/*.{scss,html}
frontend/src/app/shared/components/browser-mockup/browser-mockup.component.scss
frontend/src/app/shared/components/verify-banner/verify-banner.component.scss
frontend/src/app/features/services/services.component.scss
frontend/src/app/features/portfolio/detail/portfolio-detail.component.scss
frontend/src/app/features/admin/**/*.scss   (status color tokens only)
frontend/src/app/features/admin/dashboard/admin-dashboard.component.html
frontend/src/app/features/dashboard/**/*.scss
frontend/src/app/features/team/team.component.scss
frontend/src/app/features/blog/list/blog-list.component.scss
frontend/src/app/core/services/razorpay-checkout.service.ts          (checkout widget accent color)
```

## Verified
- `ng build --configuration development` completes cleanly — no SCSS or
  template errors from the sweep, all 6 prerendered SSR routes build.
- Compiled CSS contains both the dark and light `--color-*` blocks.
- Prerendered HTML confirms `data-theme="dark"` on `<html>` by default and
  the toggle button is present in the server-rendered markup.
