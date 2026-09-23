import { canonicalUnit } from './displayMetricsModel';

const YUAN_PER_UNIT = Object.freeze({ YUAN: 1, TEN_THOUSAND: 1e4, HUNDRED_MILLION: 1e8 });

function amountInYuan(item) {
  if (item?.state !== 'READY' || item.rawValue === null || item.rawValue === undefined
    || item.rawValue === '' || typeof item.rawValue === 'boolean') return null;
  const value = Number(item.rawValue);
  const multiplier = YUAN_PER_UNIT[canonicalUnit(item.sourceUnit)];
  return Number.isFinite(value) && multiplier ? value * multiplier : null;
}

/** 仅对明确的两项同口径金额计算包含占比；异常值不绘制比例。 */
export function buildRevenueShareModel(operating, intermediary) {
  const total = amountInYuan(operating);
  const part = amountInYuan(intermediary);
  const ready = total !== null && total > 0 && part !== null && part >= 0 && part <= total;
  if (!ready) return { ready: false, share: null, remaining: null, shareText: '占比待核对' };
  const share = part / total * 100;
  return {
    ready: true,
    share,
    remaining: 100 - share,
    shareText: `${share.toFixed(1)}%`
  };
}
