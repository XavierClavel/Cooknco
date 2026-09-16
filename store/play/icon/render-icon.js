// Renders the Play Store icon from the adaptive launcher icon's own vector source, so the
// store listing shows the same drawing the launcher does rather than an upscale of the
// 192px xxxhdpi webp.
//
// The 108x108 adaptive viewport keeps its outer 18 units as bleed: a launcher only ever
// shows the central 72x72. Cropping to that region is what makes the tomato appear at the
// size it does on a handset — rendering the whole 108 would shrink it by a quarter.
//
// Group transforms follow VectorDrawable's order, translate . rotate . scale with a 0,0
// pivot, which is the same order SVG reads a transform list in.
const sharp = require('sharp');
const path = require('path');

const BG = '#F0F4EF';   // CookncoBackground, values/colors.xml brand_background
const BODY = '#ED6D6B'; // logo.svg .cls-3
const STEM = '#429775';
const OUTLINE = '#06151E';

const tomato = `
  <g transform="translate(-30.2,19.56) scale(2.5)">
    <path d="M42.44,16.5c0,4.59 -3.92,8.16 -8.76,7.97s-8.76,-4.06 -8.76,-8.65 3.92,-8.16 8.76,-7.97 8.76,4.06 8.76,8.65Z"
          fill="${BODY}" stroke="${OUTLINE}" stroke-width="2" stroke-linejoin="round"/>
    <g transform="translate(4.74,-12.4) rotate(20.38)">
      <path d="M34.96,4.75h0c0.75,0 1.36,0.61 1.36,1.36v1.91c0,1.18 -0.96,2.14 -2.14,2.14h0c-1.18,0 -2.14,-0.96 -2.14,-2.14v-0.36c0,-1.61 1.31,-2.92 2.92,-2.92Z"
            fill="${STEM}" stroke="${OUTLINE}" stroke-width="2" stroke-linejoin="round"/>
    </g>
  </g>`;

// Square and fully opaque, with no rounding of its own: Play masks and rounds the icon
// itself, and a pre-rounded one comes out with the corners cut twice.
const svg = (size) => `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="18 18 72 72">
  <rect x="18" y="18" width="72" height="72" fill="${BG}"/>
  ${tomato}
</svg>`;

// Full-bleed variant: the whole 108 viewport, which is what Play's "adaptive icon" slot and
// any launcher doing its own masking want.
const svgFullBleed = (size) => `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 108 108">
  <rect width="108" height="108" fill="${BG}"/>
  ${tomato}
</svg>`;

const out = process.argv[2];
const jobs = [
  ['icon/play-store-icon-512.png', svg(512), 512],
  ['icon/icon-masked-1024.png', svg(1024), 1024],
  ['icon/icon-fullbleed-1024.png', svgFullBleed(1024), 1024],
];

(async () => {
  for (const [rel, markup, size] of jobs) {
    const file = path.join(out, rel);
    // The SVG declares its size in px, so it rasterises 1:1 at the target — no density
    // multiplier, which would ask libvips for a 17000px intermediate and be refused.
    await sharp(Buffer.from(markup))
      // Flattened onto the brand ground: Play rejects an icon with an alpha channel.
      .flatten({ background: BG })
      .png({ compressionLevel: 9 })
      .toFile(file);
    const meta = await sharp(file).metadata();
    console.log(`${rel}  ${meta.width}x${meta.height}  ${meta.channels}ch  alpha=${meta.hasAlpha}`);
  }
})();

// Usage, from the repository root — `sharp` is not a dependency of anything here, so it is
// fetched into a throwaway directory rather than added to the frontend's package.json for an
// asset that gets rebuilt once a year:
//
//   d=$(mktemp -d) && npm --prefix "$d" install sharp --silent \
//     && NODE_PATH="$d/node_modules" node store/play/icon/render-icon.js store/play \
//     && rm -rf "$d"
