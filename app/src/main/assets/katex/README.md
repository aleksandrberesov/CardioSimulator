# KaTeX assets (bundled, offline)

`LectureWebView` renders lecture math with KaTeX served **locally** through
`WebViewAssetLoader` at `https://appassets.androidplatform.net/assets/katex/`.
No network/CDN is used (the WebView blocks remote loads).

These binaries are **not** checked in. Before building, download the KaTeX
release (version in [`VERSION`](VERSION)) and place its files here so the
final layout is:

```
app/src/main/assets/katex/
├── katex.min.css
├── katex.min.js
├── auto-render.min.js          ← contrib/auto-render.min.js in the release
├── VERSION
├── README.md
└── fonts/
    ├── KaTeX_Main-Regular.woff2
    ├── KaTeX_Math-Italic.woff2
    └── …  (all KaTeX_*.woff2 / .woff / .ttf from the release `fonts/` dir)
```

## How to fetch

1. Download `katex.tar.gz` from the release:
   https://github.com/KaTeX/KaTeX/releases (match `VERSION`).
2. Copy `katex.min.css`, `katex.min.js`, `fonts/` and
   `contrib/auto-render.min.js` into this folder (keep `fonts/` as a
   subfolder; `katex.min.css` references `fonts/KaTeX_*` relatively).

## Graceful degradation

`LectureWebView` guards the KaTeX call on `window.renderMathInElement`, so if
these files are missing the page still loads — formulas just show as raw
`$...$` source instead of crashing. Add the files to get rendered math.

## APK size

The `.woff2` fonts are already compressed; add them to the build's
no-compress list (Phase 5) so they aren't re-compressed in the APK.
