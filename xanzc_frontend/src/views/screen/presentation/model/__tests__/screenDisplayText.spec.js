import { describe, expect, it } from 'vitest';
import { screenDisplayText } from '../screenDisplayText';

describe('screenDisplayText', () => {
  it('只清理展示文字中的临时指标前缀，保留可辨识的业务名称', () => {
    expect(screenDisplayText('测试_直营存款余额')).toBe('存款余额');
    expect(screenDisplayText('测试_直营存款较上月净增')).toBe('存款较上月净增');
    expect(screenDisplayText('测试_对公贷款目标完成率')).toBe('联调对公贷款目标完成率');
    expect(screenDisplayText('手工测试收入')).toBe('手工联调收入');
    expect(screenDisplayText('测试')).toBe('联调');
    expect(screenDisplayText('存款余额')).toBe('存款余额');
  });
});
