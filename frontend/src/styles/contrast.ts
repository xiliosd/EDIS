function channelToLinear(channel: number): number {
  const value = channel / 255;
  return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
}

function relativeLuminance(hexColor: string): number {
  const hex = hexColor.replace('#', '');
  const [red, green, blue] = [0, 2, 4].map((start) =>
    channelToLinear(parseInt(hex.slice(start, start + 2), 16)),
  );
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
}

/** Razón de contraste WCAG 2.1 entre dos colores #rrggbb. */
export function contrastRatio(foreground: string, background: string): number {
  const [lighter, darker] = [relativeLuminance(foreground), relativeLuminance(background)].sort(
    (a, b) => b - a,
  );
  return (lighter + 0.05) / (darker + 0.05);
}
