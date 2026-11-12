/**
 * Colour helpers for the category badge and the colour picker.
 *
 * Colours are stored as `#RRGGBB`; the picker works in HSV because that is the space a gradient
 * can be dragged through, and hex is what the API and the database hold.
 */

/** `#01916D` + 0.18 -> `rgba(1, 145, 109, 0.18)`. Falls back to the input when it is not a hex. */
export function withAlpha(hex: string, alpha: number): string {
  const match = /^#([0-9a-fA-F]{6})$/.exec(hex.trim());
  if (!match) return hex;

  const value = parseInt(match[1], 16);
  const r = (value >> 16) & 255;
  const g = (value >> 8) & 255;
  const b = value & 255;
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

/** HSV (h in degrees, s and v from 0 to 1) to `#RRGGBB`. */
export function hsvToHex(h: number, s: number, v: number): string {
  const chroma = v * s;
  const sector = ((h % 360) + 360) % 360 / 60;
  const second = chroma * (1 - Math.abs((sector % 2) - 1));

  let rgb: [number, number, number];
  if (sector < 1) rgb = [chroma, second, 0];
  else if (sector < 2) rgb = [second, chroma, 0];
  else if (sector < 3) rgb = [0, chroma, second];
  else if (sector < 4) rgb = [0, second, chroma];
  else if (sector < 5) rgb = [second, 0, chroma];
  else rgb = [chroma, 0, second];

  const match = v - chroma;
  const channel = (n: number) =>
    Math.round((n + match) * 255)
      .toString(16)
      .padStart(2, '0')
      .toUpperCase();

  return `#${channel(rgb[0])}${channel(rgb[1])}${channel(rgb[2])}`;
}

/** `#RRGGBB` to HSV, or null when the string is not a full hex colour. */
export function hexToHsv(hex: string): { h: number; s: number; v: number } | null {
  const match = /^#([0-9a-fA-F]{6})$/.exec(hex.trim());
  if (!match) return null;

  const value = parseInt(match[1], 16);
  const r = ((value >> 16) & 255) / 255;
  const g = ((value >> 8) & 255) / 255;
  const b = (value & 255) / 255;

  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const delta = max - min;

  let h = 0;
  if (delta !== 0) {
    if (max === r) h = 60 * (((g - b) / delta) % 6);
    else if (max === g) h = 60 * ((b - r) / delta + 2);
    else h = 60 * ((r - g) / delta + 4);
  }
  if (h < 0) h += 360;

  return { h, s: max === 0 ? 0 : delta / max, v: max };
}
