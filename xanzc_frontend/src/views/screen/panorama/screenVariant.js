export const BRANCH_MAP_V2_SCREEN_CODE = 'SCR_PROVINCE_MAP_V2';

export function isBranchMapV2(source) {
  return String(source?.screenCode ?? source?.screen_code ?? '').trim() === BRANCH_MAP_V2_SCREEN_CODE;
}
