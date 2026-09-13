/*
 * Critical first-paint theme bootstrap. This file is intentionally tiny and synchronous.
 * It runs before Angular hydrates so the <html data-theme="..."> attribute is correct
 * before the main stylesheet paints. Keep it dependency-free and same-origin.
 */
(function () {
  var THEME_KEY = 'neelastack_theme';

  try {
    var stored = localStorage.getItem(THEME_KEY);
    var theme =
      stored === 'light' || stored === 'dark'
        ? stored
        : window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches
          ? 'light'
          : 'dark';

    document.documentElement.setAttribute('data-theme', theme);
  } catch (e) {
    document.documentElement.setAttribute('data-theme', 'dark');
  }
})();
