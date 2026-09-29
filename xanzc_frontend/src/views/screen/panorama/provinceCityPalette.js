/** Stable, coordinated colors for the ten province-level cities. */
export const PROVINCE_CITY_PALETTE = Object.freeze([
  '#4f9da6', '#6e9bb7', '#7b82b5', '#9881ad', '#b29a70',
  '#7fa58f', '#b07f72', '#568caf', '#9e8fbd', '#6f9f91'
]);

export const PROVINCE_NEUTRAL_COLOR = '#65738a';

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
export function provinceCityColor(code, state = 'HAS_INSTITUTION') {
  if (String(state || '').trim().toUpperCase() !== 'HAS_INSTITUTION') return PROVINCE_NEUTRAL_COLOR;
  const value = text(code);
  const knownIndex = PROVINCE_CITY_CODES.indexOf(value);
  const index = knownIndex >= 0 ? knownIndex : hash(value) % PROVINCE_CITY_PALETTE.length;
  return PROVINCE_CITY_PALETTE[index];
}

/** Return a compact swatch for the complete province city palette. */
export function provinceCityPaletteGradient() {
  return `linear-gradient(90deg, ${PROVINCE_CITY_PALETTE.join(', ')})`;
}
