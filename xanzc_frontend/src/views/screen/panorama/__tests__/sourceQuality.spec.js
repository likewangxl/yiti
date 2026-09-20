import { describe, expect, it } from 'vitest';
import { collectSourceQualities } from '../sourceQuality';
import { resolveSourcePresentation } from '../sourcePresentation';
describe('不同来源质量溯源', () => {
  it('保留零售未接入状态，不能在正式入口丢弃来源缺项说明', () => {
    const result=resolveSourcePresentation({renderPackage:{canvasStyle:{sourceAvailability:{retailAum:{status:'NO_SOURCE',message:'没有同口径AUM来源'}}}}});
    expect(result.sourceAvailability.retailAum).toEqual({status:'NO_SOURCE',message:'没有同口径AUM来源'});
  });
  it('保留各槽日期与过期状态，不合并为一个虚假的完整批次', () => {
    const a={batchId:'A',dataDate:'2026-07-22',status:'STALE'};
    const b={batchId:'B',dataDate:'2026-08-30',status:'PARTIAL'};
    expect(collectSourceQualities({corpDeposit:{response:{quality:a}},corpLoan:{response:{quality:b}},corpTrend:{response:{rows:[]}}})).toEqual({corpDeposit:a,corpLoan:b});
    expect(collectSourceQualities({corpDeposit:{error:new Error('403')}})).toEqual({});
  });
});
