# IMPLEMENTATION-STATUS — Theme overhaul

## Done
- [x] New dark palette (ink/bone/bronze) applied via `:root`/`[data-theme="dark"]`.
- [x] New light palette applied via `[data-theme="light"]`, sourced from your
      uploaded `neelastack-theme.css`.
- [x] All hardcoded colors across `frontend/src/app/**/*.{scss,html}` swept
      and replaced with theme variables (status colors, glow rgba effects,
      the architecture diagram SVG, navbar translucency).
- [x] Animated dark/light toggle component, wired into desktop + mobile nav.
- [x] No-flash theme bootstrap (inline script in `index.html` + SSR default).
- [x] `localStorage` persistence of the visitor's choice.
- [x] Production dev-mode build verified clean (`ng build --configuration development`),
      SSR prerender of all 6 static routes succeeds.
- [x] Razorpay checkout accent color updated to match the new bronze.
- [x] `CHANGES-theme-overhaul.md` written.

## Not done / not in scope for this pass
- **Full production build (`--configuration production`) was not run to
  completion** — it exceeded the sandbox's time budget partway through the
  optimization/minification phase (dev build did complete and compiles the
  same source cleanly, so this is very unlikely to surface new errors, but
  it hasn't been directly confirmed). Recommend running
  `npm run build` yourself once before deploying, or let me know and I'll
  retry it in smaller steps.
- **Visual QA in a real browser** — I verified the compiled CSS contains
  both theme blocks and that the SSR HTML carries the toggle and correct
  `data-theme` attribute, but I have not visually inspected every page in
  both themes (there are ~40 routes/components). The color mapping was done
  systematically (every hardcoded color found via full-codebase grep was
  either mapped to a variable or deliberately left as-is with a documented
  reason), so I'd expect it to look right everywhere, but a manual pass over
  admin pages, the dashboard, and the estimator/quote flows would catch
  anything subtle (e.g. an icon or chart library rendering its own colors
  outside CSS) that a text search can't.
- **Contrast/accessibility audit** — I picked light-theme status colors
  (`--color-danger`, `--color-success`, `--color-info`) with WCAG contrast
  in mind, but didn't run an automated contrast checker across every
  color/background pairing in the app.
- **Backend, migrations, content, SEO, deployment config, CI/CD** —
  untouched, as requested; this pass is frontend theme only.

## Suggested next step
Run the app locally (`npm start` or `ng serve`) and click through a few key
pages (home, an admin list page, the estimator) toggling light/dark, since
that's faster and more reliable than me guessing further from source alone.
