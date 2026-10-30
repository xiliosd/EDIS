function channelsOf(hexColor: string): number[] {
  const hex = hexColor.replace('#', '');
  return [0, 2, 4].map((start) => parseInt(hex.slice(start, start + 2), 16));
}

function channelToLinear(channel: number): number {
  const value = channel / 255;
  return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
}

function relativeLuminance(hexColor: string): number {
  const [red, green, blue] = channelsOf(hexColor).map(channelToLinear);
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
}

/** Razón de contraste WCAG 2.1 entre dos colores #rrggbb. */
export function contrastRatio(foreground: string, background: string): number {
  const [lighter, darker] = [relativeLuminance(foreground), relativeLuminance(background)].sort(
    (a, b) => b - a,
  );
  return (lighter + 0.05) / (darker + 0.05);
}

/** Color #rrggbb que resulta de pintar `foreground` con opacidad `alpha` sobre `background`. */
export function blendOver(foreground: string, background: string, alpha: number): string {
  const top = channelsOf(foreground);
  const bottom = channelsOf(background);
  return `#${top
    .map((channel, index) => Math.round(channel * alpha + bottom[index] * (1 - alpha)))
    .map((channel) => channel.toString(16).padStart(2, '0'))
    .join('')}`;
}
