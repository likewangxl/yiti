/** Preserve per-source evidence; different data dates never imply one shared batch. */
export function collectSourceQualities(results = {}) {
  return Object.fromEntries(Object.entries(results).flatMap(([slot, result]) => {
    const quality = result?.response?.quality;
    return !result?.error && quality && typeof quality === 'object' ? [[slot, quality]] : [];
  }));
}
