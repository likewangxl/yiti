/** Stable, high-contrast colors for the ten province-level cities. */
export const PROVINCE_CITY_PALETTE = Object.freeze([
  '#2ee6d6', '#3b82f6', '#8b5cf6', '#f472b6', '#f59e0b',
  '#22c55e', '#f97316', '#06b6d4', '#eab308', '#ef4444'
]);

const PROVINCE_CITY_CODES = Object.freeze([
  '610100', '610200', '610300', '610400', '610500',
  '610600', '610700', '610800', '610900', '611000'
]);

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

function hash(value) {
  let result = 2166136261;
  for (const character of value) {
    result ^= character.charCodeAt(0);
    result = Math.imul(result, 16777619);
  }
  return result >>> 0;
}

/** Resolve a city code to a stable palette entry, including unknown codes. */
export function provinceCityColor(code) {
  const value = text(code);
  const knownIndex = PROVINCE_CITY_CODES.indexOf(value);
  const index = knownIndex >= 0 ? knownIndex : hash(value) % PROVINCE_CITY_PALETTE.length;
  return PROVINCE_CITY_PALETTE[index];
}

/** Return a compact swatch for the complete province city palette. */
export function provinceCityPaletteGradient() {
  return `linear-gradient(90deg, ${PROVINCE_CITY_PALETTE.join(', ')})`;
}
