# Store assets

What the Play listing is made of, kept in the repository so the live listing has a source
rather than existing only inside the console.

**Nothing here is uploaded by CI, deliberately.** `app/fastlane/Fastfile` passes
`skip_upload_metadata`, `skip_upload_images` and `skip_upload_screenshots`, so a release
touches the binary and nothing else. Without those flags fastlane treats the repository as
authoritative and a missing `fastlane/metadata/` directory would **wipe the live listing** —
the copy, the screenshots, every locale. The listing is edited by hand in the console; this
directory is what you paste from, and the record of what was pasted.

That also means a change here ships nothing. Editing `listing-en.md` and merging does not
update the store; someone has to paste it into the console.

```
store/play/
  icon/                 the launcher mark, rendered for the console
  feature-graphic/      1024x500, the banner at the top of the listing
  phone/                phone screenshots, in display order
  tablet7/              7-inch tablet screenshots
  tablet10/             10-inch tablet screenshots
  listing/              the copy, one file per locale
```

## What Play requires

| Slot | Spec | Notes |
| --- | --- | --- |
| App icon | 512×512 PNG, 32-bit, **no alpha** | Play rounds it itself — do not pre-round |
| Feature graphic | 1024×500 PNG or JPEG, no alpha | Mandatory. Shown before the screenshots, and it is what a Play listing card uses |
| Phone screenshots | 2–8, 16:9 or 9:16, each side 320–3840px | 2 is the minimum Play will publish with |
| 7" tablet | up to 8, same bounds | Optional, but without them the tablet listing shows the phone shots letterboxed |
| 10" tablet | up to 8, same bounds | Same |
| Short description | ≤ 80 characters | |
| Full description | ≤ 4000 characters | |

Screenshots must show the app, not a device frame with marketing text around it — Play
rejects mockups that misrepresent what the app looks like.

## The icon

`icon/play-store-icon-512.png` is **rendered from the adaptive launcher icon's own vector
source** (`app/androidApp/src/main/res/drawable/ic_launcher_{background,foreground}.xml`),
not upscaled from the 192px `mipmap-xxxhdpi/ic_launcher.webp`. The vectors are in turn the
paths from `frontend/src/assets/logo.svg`, so the store icon, the launcher and the website
are all one drawing.

The 108×108 adaptive viewport keeps its outer 18 units as bleed — a launcher only ever shows
the central 72×72 — so the render crops to that region. Rendering the full 108 would put the
mark at three-quarters the size it appears on a handset.

- `play-store-icon-512.png` — what the console wants
- `icon-masked-1024.png` — the same framing at 2×, for anything else that asks
- `icon-fullbleed-1024.png` — the whole 108 viewport, for a slot that does its own masking

To regenerate after changing the logo, run `icon/render-icon.js` — it holds an SVG
transcription of the two vector drawables and the usage line at the bottom of the file. If the
logo changes, the paths in it have to be re-copied from the drawables; it is a renderer, not a
converter.

⚠️ The legacy raster mipmaps (`mipmap-*/ic_launcher.webp`, used only on API 24–25) place the
stem differently from the adaptive vector, so they and this icon are not the same drawing.
The adaptive icon is what effectively every device shows; the webps look like they predate a
later tweak to the stem and are worth regenerating from the vector if anyone cares about the
two oldest API levels.

## Naming

The app is inconsistent about its own name and this is not the place to fix it:
`app_name` is **Cooknco**, the notification channel and the website title are **Cook&Co**.
Whatever the Play listing title is today, leave it alone unless you are changing it
deliberately — the title is what people search for.
