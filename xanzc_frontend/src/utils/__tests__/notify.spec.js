import { describe, it, expect } from 'vitest';
import { parseApprovalNode } from '../notify';

describe('parseApprovalNode —— 从通知正文提取「当前审批环节」', () => {
  it('提取"当前审批环节："与"。"之间的内容', () => {
    expect(parseApprovalNode('您提交的…已提交 22 天仍未办结，当前审批环节：机构负责人审批·3级。请关注审批进度或联系审批人跟进。'))
      .toBe('机构负责人审批·3级');
    expect(parseApprovalNode('…当前审批环节：审批中。请…')).toBe('审批中');
  });
  it('无该字段 / 空 / null / undefined → 空串', () => {
    expect(parseApprovalNode('普通系统通知，无审批环节字样')).toBe('');
    expect(parseApprovalNode('')).toBe('');
    expect(parseApprovalNode(null)).toBe('');
    expect(parseApprovalNode(undefined)).toBe('');
  });
});
