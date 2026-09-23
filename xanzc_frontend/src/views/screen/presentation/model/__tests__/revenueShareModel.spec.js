import { describe, expect, it } from 'vitest';
import { buildRevenueShareModel } from '../revenueShareModel';

const item = (rawValue, sourceUnit, text, state = 'READY') => ({ rawValue, sourceUnit, text, state });

describe('营业收入包含中间业务收入', () => {
  it('将不同金额单位换算后展示中收占营业收入及剩余收入', () => {
    expect(buildRevenueShareModel(item(200000000, 'YUAN', '2.00亿元'), item(5000, 'TEN_THOUSAND', '5000万元')))
      .toMatchObject({ ready: true, share: 25, remaining: 75, shareText: '25.0%' });
  });

  it('零分母、缺数、负数、超出总额和不可换算单位不绘制比例', () => {
    const operating = item(100, 'YUAN', '100元');
    for (const [total, fee] of [
      [item(0, 'YUAN', '0元'), item(0, 'YUAN', '0元')],
      [operating, item(null, 'YUAN', '—', 'NO_VALUE')],
      [operating, item(-1, 'YUAN', '-1元')],
      [operating, item(101, 'YUAN', '101元')],
      [operating, item(5, 'PERCENT', '5%')]
    ]) {
      expect(buildRevenueShareModel(total, fee)).toMatchObject({ ready: false, share: null });
    }
  });
});
