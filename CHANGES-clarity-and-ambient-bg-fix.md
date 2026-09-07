# CHANGES — Clarity & ambient background fix

Follow-up to `CHANGES-premium-depth-effects.md`. That pass added shadows and
cursor tilt but didn't touch color/typography — this pass addresses feedback
that the site read as dull and hard to read.

## What was actually wrong

### 1. The hero headline was rendering half-transparent
`frontend/src/app/features/home/home.component.scss` had:
```scss
.line:last-child {
  color: var(--color-text);
  background: linear-gradient(90deg, var(--color-text) 60%, var(--color-teal));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
```
This made the entire second line ("that holds up.") fade from bone-white
into a muted teal via `-webkit-text-fill-color: transparent` — the single
biggest reason the page read as washed-out, since it's the most prominent
text on the homepage. The reference instead uses fully opaque text with a
plain bronze underline under just the accent phrase.

**Fixed:** split the heading so only the actual accent phrase gets special
treatment, and replaced the transparent gradient with solid text + a border:

- `home.component.html`: `<span class="line">Software</span><span
  class="line">that holds up.</span>` → `<span class="line">Software
  that</span><span class="line line-accent">holds up.</span>` (matches the
  reference's own two-line split).
- `home.component.scss`: `.line-accent` is now `color: var(--color-text)`
  (fully opaque) with `border-bottom: 3px solid var(--color-amber)`,
  `display: inline-block; align-self: flex-start` so the underline hugs just
  that phrase instead of stretching full-width.

Checked the rest of the codebase for the same trick
(`grep -rl "text-fill-color\|background-clip: text"`) — this was the only
occurrence.

### 2. Everything outside the hero was a single flat color
`.bg-grid` (the grid-lines + amber/teal glow backdrop) already existed and
looked good, but it was only ever applied to two sections on the homepage
(`.hero`, `.cta-band`). Every other section, on every other page, sat on a
plain, textureless `--color-bg` fill — that's the "dull background" being
described.

**Fixed:** added a fixed, low-opacity version of the same grid+glow
treatment directly on `body` (new `body::before`/`::after` rules in
`styles.scss`), so it sits behind the *entire* app on every route instead of
two isolated patches, without touching every page's markup. `z-index: -1`
and `pointer-events: none` keep it strictly decorative — solid surfaces
(`.card`, the navbar's glass blur, footer, form fields) still render cleanly
on top of it; it only shows in the empty space between them.

### 3. Secondary text/label color was murkier than it needed to be
Dark theme (the default) had `--color-text-muted: #9a968c` and `--color-teal:
#5c8a82` — both readable by contrast math (~7.5:1 and ~5:1 against
`#0c0c0d`), but muddier than they needed to be for how much of the page's
copy (ledes, section-subs, stat labels, eyebrows) relies on them.

**Fixed:** brightened both, dark theme only (light theme's contrast was
already comfortably high):
- `--color-text-muted`: `#9a968c` → `#b0ada2` (contrast vs. `#0c0c0d`:
  ~7.5:1 → ~8.7:1)
- `--color-teal`: `#5c8a82` → `#6fa89c` (contrast vs. `#0c0c0d`: ~5.0:1 →
  ~7.2:1)
- Matching `-rgb` triplets updated so every `rgba()` glow/overlay that reads
  those tokens (stat-flag dot glow, trust-item icons, `.system-line`, the
  new ambient backdrop) stays in sync.

## Files touched
```
frontend/src/styles.scss                       (ambient body::before/::after backdrop; brightened --color-text-muted / --color-teal + rgb triplets, dark theme only)
frontend/src/app/features/home/home.component.html   (heading split into "Software that" / "holds up.")
frontend/src/app/features/home/home.component.scss   (removed the transparent-text gradient; solid text + bronze underline via .line-accent)
```

## Not verified in this environment
Same caveat as the last round — no `node_modules`/network here, so `ng
build` wasn't run. Brace-balance and contrast ratios were checked by hand;
please build and eyeball it before deploying.
