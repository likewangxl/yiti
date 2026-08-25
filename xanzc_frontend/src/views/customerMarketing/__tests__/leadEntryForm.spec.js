import { describe, expect, it } from 'vitest';
import {
  createLeadEntryInitialState,
  createLeadEntryRules,
  getDistributionHelp
} from '../leadEntryForm';

describe('线索录入表单', () => {
  it('经营属性全部为非必填且新建时不预设值', () => {
    const form = createLeadEntryInitialState();
    const rules = createLeadEntryRules(form);
    const operatingFields = [
      'industry', 'customerType', 'groupType', 'groupName', 'enterpriseType',
      'leadSource', 'isKeystone', 'isAccountOpened', 'creditAmount',
      'creditExposureAmount', 'customerDesc'
    ];

    operatingFields.forEach((field) => {
      expect((rules[field] || []).some(rule => rule.required)).toBe(false);
      expect(form[field] === '' || form[field] == null).toBe(true);
    });
  });

  it('基础信息与分配信息原有必填校验保持不变', () => {
    const form = createLeadEntryInitialState();
    const rules = createLeadEntryRules(form);

    expect(rules.custName.some(rule => rule.required)).toBe(true);
    expect(rules.unifiedCreditCode.some(rule => rule.required)).toBe(true);
    expect(rules.distributionMode.some(rule => rule.required)).toBe(true);
  });

  it('触达限制默认开启且必须明确选择是或否', () => {
    const form = createLeadEntryInitialState();
    const rules = createLeadEntryRules(form);

    expect(form.touchRestricted).toBe(1);
    expect(rules.touchRestricted.some(rule => rule.required)).toBe(true);
  });

  it('指定客户经理范围提示审批通过后直接进入已认领客户池', () => {
    expect(getDistributionHelp('SCOPE')).toBe('审批通过后直接进入指定客户经理的已认领客户池。');
  });
});
