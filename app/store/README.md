# Store assets

What the Play Console listing shows, drawn from what the product already has rather than
authored a second time: the ground is `CookncoBackground` (#F0F4EF), the mark is the two
paths of `frontend/src/assets/logo.svg`, and the type is Geologica, which the web app sets
and the app bundles.

Each asset is an HTML page rendered by headless Chromium at exactly the size Play asks
for — the same engine that prints the PDF exports, and the only renderer this repository
already depends on. Nothing is scaled after the fact, so editing the page and re-running
the script is the whole workflow:

```sh
sh app/store/render.sh          # writes out/, CHROME=… if yours is somewhere unusual
```

| Source | Output | Size | Play asset |
| --- | --- | --- | --- |
| `icon.html` | `out/icon-512.png` | 512×512, 32-bit | App icon |
| `feature-graphic.html` | `out/feature-graphic-1024x500.png` | 1024×500, 24-bit | Feature graphic |
| `feature-graphic-green.html` | `out/feature-graphic-1024x500-green.png` | 1024×500, 24-bit | Feature graphic, alternative ground |

`listing-fr-FR.md` and `listing-en-GB.md` hold the short and full descriptions, at the lengths
the console enforces (80 and 4000 characters). They mirror each other section for section and
are not translations of each other's sentences — when a feature changes, both change in the
same commit, because a listing accurate in one locale and stale in the other is the version
nobody notices.

The feature graphics are **French-only**: the tagline is baked into the page. An English
listing wants its own, and the console takes one per locale — the copy to change is in
`feature-graphic.html`.

Three things worth knowing before editing any of it:

- **The icon is the launcher icon, not a new drawing.** Same tomato, same cream, at the
  proportion a launcher shows once it has masked the adaptive icon — so the listing and the
  home screen are one icon. The wordmark is absent for the reason it is absent from
  `ic_launcher_foreground.xml`: two lines of Geologica Black are unreadable at that size.
  Changing the mark means changing `drawable/ic_launcher_*.xml` in the same commit, or the
  two drift apart where a user sees them side by side.
- **The wordmark is live type, and Geologica Black is not a weight the app bundles.**
  `logo.svg` carries no path form of "COOK'N'CO" — it names the font — so the lockup here
  reproduces it as text at the SVG's own coordinates, spacing included. `geologica_black.ttf`
  sits beside these pages for that: the 900 instance from Google Fonts, under the OFL the
  app already ships (`composeApp/licenses/GEOLOGICA_OFL.txt`), and **not** one of the four
  weights in `composeApp/src/commonMain/composeResources/font/` — those stop at 700, and
  the logo at 700 is not the logo.
- **Nothing here is uploaded by CI.** `fastlane/Fastfile` passes `skip_upload_metadata`,
  `skip_upload_images` and `skip_upload_screenshots` on both lanes, precisely so that a
  build never overwrites a listing edited in the console. These files are the source the
  listing is kept from, by hand.

Still missing for a complete listing: the phone screenshots (Play wants at least two, and
a 7-inch and 10-inch set if tablets are declared), which are captures of the app rather
than something to draw.
