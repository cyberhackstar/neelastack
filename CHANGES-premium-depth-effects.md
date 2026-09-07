# CHANGES — Premium depth & motion pass

Scope: **visual polish only**, layered on top of the existing "ink & bone +
bronze" theme from `CHANGES-theme-overhaul.md`. No routes, business logic,
backend, or content changed. Goal: bring the cursor-tracking 3D tilt and
layered shadow depth from the reference hero-card demo to the cards already
using the old static tilt, and give previously-flat surfaces (plain cards,
stat blocks, glass panels, primary buttons) the same sense of elevation.

## What changed

### 1. Elevation tokens — `frontend/src/styles.scss`
Added a small set of theme-aware shadow tokens next to the existing color
tokens, so every "raised surface" pulls from one shared system instead of a
one-off `box-shadow` value:

- `--shadow-sm` / `--shadow-md` / `--shadow-lg` — layered (tight core + soft
  spread) shadows, tuned separately per theme. Dark uses near-black; light
  uses low-opacity warm ink instead of black, so raised cards don't look
  muddy on the bone background.
- `--shadow-glow-amber` / `--shadow-glow-teal` — the same layered structure
  with a tint, for interactive/accent surfaces (primary buttons).

Applied to surfaces that had no shadow before: `.card` (base + hover), the
CTA/auth submit buttons, `.glass-panel` (hero visual, featured panels),
`.stat-block`. `.card-tilt:hover` now also elevates to `--shadow-lg` on top
of its existing rotation.

### 2. Cursor-tracking 3D tilt — new `TiltDirective`
`frontend/src/app/shared/directives/tilt.directive.ts` (new) is a standalone
directive with a **class selector** (`.card-tilt`), so it attaches to every
existing `.card-tilt` element without any template changes — only each
component's `imports: [...]` array needed the directive added:

```
frontend/src/app/features/blog/detail/blog-detail.component.ts
frontend/src/app/features/blog/list/blog-list.component.ts
frontend/src/app/features/dashboard/list/dashboard-list.component.ts
frontend/src/app/features/home/home.component.ts
frontend/src/app/features/portfolio/list/portfolio-list.component.ts
frontend/src/app/features/services/services.component.ts
frontend/src/app/features/solutions/list/solutions-list.component.ts
frontend/src/app/features/team/team.component.ts
```

On mousemove, it computes rotateX/rotateY from the cursor's position inside
the card and sets `transform` as an inline style (via `Renderer2`); on
mouseleave it removes that inline style, so the plain CSS `.card-tilt:hover`
rule in `styles.scss` is exactly what's left underneath. That old rule was
kept, unmodified in behavior, as the fallback for keyboard focus, touch, and
`prefers-reduced-motion` — the directive checks the same
`(pointer: fine) && !prefers-reduced-motion` guard already used by the hero
parallax in `HomeComponent`, and does nothing at all otherwise.

### 3. Left untouched
`browser-mockup` component already had a hand-tuned static 3D tilt + layered
shadow + hover zoom (`:host-context(.card-tilt:hover)`) from an earlier pass
— not touched, it's independent of and unaffected by the new directive
(different property, driven by real `:hover`, not by the parent's inline
transform). Colors, typography, layout, and the theme toggle are unchanged.

## Files touched
```
frontend/src/styles.scss                                              (shadow tokens + applied to .card/.glass-panel/.stat-block/.btn-primary/auth submit button)
frontend/src/app/shared/directives/tilt.directive.ts                  (new)
frontend/src/app/features/blog/detail/blog-detail.component.ts
frontend/src/app/features/blog/list/blog-list.component.ts
frontend/src/app/features/dashboard/list/dashboard-list.component.ts
frontend/src/app/features/home/home.component.ts
frontend/src/app/features/portfolio/list/portfolio-list.component.ts
frontend/src/app/features/services/services.component.ts
frontend/src/app/features/solutions/list/solutions-list.component.ts
frontend/src/app/features/team/team.component.ts
```

## Not verified in this environment
`node_modules` wasn't part of the uploaded project, and this environment has
no network access to `npm install`, so `ng build` could not be run here.
Every edit was reviewed by hand (brace-balance check on the SCSS, import
paths resolved on disk, decorator arrays diffed), but please run your usual
`ng build` / `ng test` before deploying.
