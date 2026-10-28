/** Coordinated, stable colors for city-level district classification. */
export const CITY_DISTRICT_PALETTE = Object.freeze([
  '#39b8c6', '#4b96e8', '#6f79e8', '#936be4', '#bd62ce', '#dc619f',
  '#e96e78', '#ed8c57', '#e2ae4d', '#c1c450', '#8fc15a', '#62bd78',
  '#4cbd9b', '#45aeb7', '#5c9fd3', '#7f91df', '#a981d5', '#cb79b9',
  '#dc817f', '#d99d5a', '#b6af5c', '#83ad69', '#65aa8e', '#7298b7'
]);

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

/** Map unique district codes in sorted order so GeoJSON ordering cannot change their colors. */
export function cityDistrictColors(codes = []) {
  const sortedCodes = [...new Set((Array.isArray(codes) ? codes : []).map(text).filter(Boolean))].sort();
  return Object.fromEntries(sortedCodes.map((code, index) => [
    code,
    CITY_DISTRICT_PALETTE[index % CITY_DISTRICT_PALETTE.length]
  ]));
}

/** Return a compact swatch covering the full district classification palette. */
export function cityDistrictPaletteGradient() {
  return `linear-gradient(90deg, ${CITY_DISTRICT_PALETTE.join(', ')})`;
}
