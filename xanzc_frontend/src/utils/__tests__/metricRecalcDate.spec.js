import { describe, expect, it } from 'vitest';
import dayjs from 'dayjs';
import {
  METRIC_RECALC_DATE_MESSAGES,
  getMetricRecalcDateError,
  isMetricJobKey,
  isMetricRecalcDateDisabled,
  yesterdayDate
} from '../metricRecalcDate';

const TODAY = '2026-08-14';

describe('metricRecalcDate', () => {
  it('uses local calendar days and rejects today and future dates', () => {
    expect(getMetricRecalcDateError(TODAY, TODAY)).toBe(METRIC_RECALC_DATE_MESSAGES.TODAY_OR_FUTURE);
    expect(getMetricRecalcDateError('2026-08-15', TODAY)).toBe(METRIC_RECALC_DATE_MESSAGES.TODAY_OR_FUTURE);
    expect(isMetricRecalcDateDisabled(new Date(2026, 7, 14), dayjs(TODAY))).toBe(true);
  });

  it('allows yesterday and exactly twenty calendar days ago', () => {
    expect(getMetricRecalcDateError('2026-08-13', TODAY)).toBeNull();
    expect(getMetricRecalcDateError('2026-07-25', TODAY)).toBeNull();
    expect(isMetricRecalcDateDisabled(new Date(2026, 6, 25), dayjs(TODAY))).toBe(false);
  });

  it('allows an older natural month end but rejects other older dates', () => {
    expect(getMetricRecalcDateError('2026-06-30', TODAY)).toBeNull();
    expect(getMetricRecalcDateError('2026-07-24', TODAY)).toBe(METRIC_RECALC_DATE_MESSAGES.OLDER_THAN_20_NON_MONTH_END);
    expect(isMetricRecalcDateDisabled(new Date(2026, 6, 24), dayjs(TODAY))).toBe(true);
  });

  it('derives yesterday with the local calendar and identifies metric jobs only', () => {
    expect(yesterdayDate(dayjs(TODAY))).toBe('2026-08-13');
    expect(isMetricJobKey('LEVEL1_METRIC_CALC')).toBe(true);
    expect(isMetricJobKey('LEVEL2_METRIC_CALC')).toBe(true);
    expect(isMetricJobKey('LEVEL3_METRIC_CALC')).toBe(true);
    expect(isMetricJobKey('PERF_METRIC_DAILY')).toBe(true);
    expect(isMetricJobKey('SYS_SESSION_CLEAN')).toBe(false);
    expect(isMetricJobKey('PERF_KPI_SCORE')).toBe(false);
  });
});
