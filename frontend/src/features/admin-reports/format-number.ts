const COUNT_FORMAT = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });
const PERCENT_FORMAT = new Intl.NumberFormat('es-CO', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});
const NO_VALUE = '—';

/** Cifra entera con separador de miles de es-CO (1234 → 1.234). */
export function formatCount(value: number | undefined): string {
  return value === undefined ? NO_VALUE : COUNT_FORMAT.format(value);
}

/** Porcentaje que el API ya entrega en escala 0-100, con dos decimales (62.5 → 62,50 %). */
export function formatPercent(value: number | undefined): string {
  return value === undefined ? NO_VALUE : `${PERCENT_FORMAT.format(value)} %`;
}
