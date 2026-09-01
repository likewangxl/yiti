import { describe, expect, it } from 'vitest';

import {
  AUDIENCE_TYPES,
  BUSINESS_TYPES,
  EXPORT_MAX_ROWS_PER_SHEET,
  FILE_TYPE_OPTIONS,
  TASK_NATURES,
  buildTaskCreatePayload,
  calculateTaskWindow,
  buildExportRequest,
  buildTaskQuery,
  buildWorkflowQuery,
  businessTypeLabel,
  formatTaskWindow,
  getExportSheetCount,
  getTaskRoute,
  linkifyDescription,
  normalizeLocalDateTime,
  normalizeAssignment,
  normalizePageResult,
  validateTaskDraft
} from '../task-domain';

describe('红色引擎任务域模型', () => {
  it('支持全部党支部、指定党支部、指定员工三种任务对象', () => {
    expect(AUDIENCE_TYPES.map((item) => item.value)).toEqual([
      'ALL_BRANCH',
      'SPECIFIED_BRANCH',
      'SPECIFIED_EMPLOYEE'
    ]);
    expect(BUSINESS_TYPES.map((item) => item.value)).toEqual(['FOUR_DIMENSION', 'GENERAL']);
  });

  it('定时任务按周期锚点计算窗口并限制自然周期长度', () => {
    expect(calculateTaskWindow('MONTH_END', 5, '2026-08-15')).toEqual({
      start: '2026-08-27',
      end: '2026-08-31'
    });
    expect(calculateTaskWindow('QUARTER_START', 5, '2026-08-15')).toEqual({
      start: '2026-07-01',
      end: '2026-07-05'
    });
    expect(() => calculateTaskWindow('WEEK_START', 8, '2026-08-15')).toThrow(/不能超过/);
  });

  it('任务草稿按对象类型校验对应目标范围', () => {
    const base = {
      title: '任务标题',
      description: '任务说明',
      typeCode: 'NOTICE',
      nature: TASK_NATURES.TEMPORARY,
      startAt: '2026-08-20 09:00:00',
      endAt: '2026-08-31 18:00:00',
      requiresFile: false
    };

    expect(validateTaskDraft({ ...base, audienceType: 'ALL_BRANCHES' }).valid).toBe(true);
    expect(validateTaskDraft({
      ...base,
      audienceType: 'SPECIFIED_BRANCH',
      targetBranchIds: []
    }).errors.targetBranchIds).toMatch(/党支部/);
    expect(validateTaskDraft({
      ...base,
      audienceType: 'SPECIFIED_EMPLOYEE',
      targetEmployeeIds: []
    }).errors.targetEmployeeIds).toMatch(/员工/);
  });

  it('任务查询只提交非空条件并始终携带分页参数', () => {
    expect(buildTaskQuery({ title: '季度', nature: 'PERIODIC', status: null }, 2, 20)).toEqual({
      pageNo: 2,
      pageSize: 20,
      title: '季度',
      taskNature: 'PERIODIC'
    });
  });

  it('审核工作台查询保留后端状态字段，不把响应 status 当作查询条件', () => {
    expect(buildTaskQuery({
      assignmentStatus: 'BRANCH_PENDING',
      submissionStatus: 'BRANCH_APPROVED'
    }, 1, 20)).toEqual({
      pageNo: 1,
      pageSize: 20,
      assignmentStatus: 'BRANCH_PENDING',
      submissionStatus: 'BRANCH_APPROVED'
    });
  });

  it('工作台查询只提交页签，由服务端按页签展开状态集合分页', () => {
    expect(buildWorkflowQuery({ title: '整改', nature: 'TEMPORARY' }, 'pending', 2, 20)).toEqual({
      pageNo: 2,
      pageSize: 20,
      title: '整改',
      taskNature: 'TEMPORARY',
      tab: 'PENDING'
    });
  });

  it('四维任务走原材料入口，其他任务走临时任务填报入口', () => {
    expect(getTaskRoute({ isFourDimension: true })).toBe('materials');
    expect(getTaskRoute({ typeCode: 'FOUR_DIMENSION' })).toBe('materials');
    expect(getTaskRoute({ typeCode: 'NOTICE' })).toBe('task-entry');
  });

  it('后端任务枚举可转为页面展示文案', () => {
    expect(businessTypeLabel('FOUR_DIMENSION')).toBe('四大维度材料上报');
    expect(businessTypeLabel('GENERAL')).toBe('普通任务');
  });

  it('文件类型选项锁定为任务服务可保存的编码快照', () => {
    expect(FILE_TYPE_OPTIONS.map((item) => item.value)).toEqual([
      'PDF',
      'DOCX',
      'XLSX',
      'PNG',
      'ZIP'
    ]);
  });

  it('周期任务缺少实例窗口时仍展示后端返回的持续时间', () => {
    expect(formatTaskWindow({
      taskNature: 'SCHEDULED',
      cycleType: 'MONTH_END',
      durationDays: 5,
      startAt: null,
      endAt: null
    })).toBe('每月末 · 持续 5 天');
  });

  it('临时任务导出不需要明细项，四维任务导出必须选择明细项', () => {
    expect(buildExportRequest({ isFourDimension: false }, [])).toEqual({ itemCodes: [] });
    expect(() => buildExportRequest({ isFourDimension: true }, [])).toThrow(/明细/);
    expect(buildExportRequest({ typeCode: 'FOUR_DIMENSION' }, ['JC_STANDARD'])).toEqual({
      itemCodes: ['JC_STANDARD']
    });
  });

  it('周期窗口按后端定位日期所在周期校验自然天数', () => {
    expect(calculateTaskWindow('MONTH_START', 31, '2026-08-15')).toEqual({
      start: '2026-08-01',
      end: '2026-08-31'
    });
    expect(() => calculateTaskWindow('MONTH_START', 29, '2026-02-15')).toThrow(/当月自然天数/);
  });

  it('周期预览按北京时间自然日解析带时区的参考时刻', () => {
    expect(calculateTaskWindow('MONTH_END', 1, '2026-08-31T16:30:00.000Z')).toEqual({
      start: '2026-09-30',
      end: '2026-09-30'
    });
  });

  it('创建任务请求映射为后端 DTO，三种对象各生成明确 target', () => {
    expect(buildTaskCreatePayload({
      nature: TASK_NATURES.TEMPORARY,
      businessType: 'GENERAL',
      title: '专项整改',
      description: '请填报',
      audienceType: 'SPECIFIED_BRANCH',
      targetBranchIds: [11, 12],
      startAt: '2026-08-20 09:00:00',
      endAt: '2026-08-31 18:00:00',
      requiresFile: true,
      allowedFileTypes: ['PDF']
    })).toEqual({
      title: '专项整改',
      description: '请填报',
      taskNature: 'TEMPORARY',
      businessType: 'GENERAL',
      cycleType: null,
      durationDays: null,
      temporaryStartTime: '2026-08-20T09:00:00',
      temporaryEndTime: '2026-08-31T18:00:00',
      requiresFile: true,
      fileTypeCodes: ['PDF'],
      targets: [
        { targetType: 'SPECIFIED_BRANCH', partyOrgId: 11 },
        { targetType: 'SPECIFIED_BRANCH', partyOrgId: 12 }
      ],
      itemCodes: []
    });
  });

  it('将页面本地日期时间格式化为后端要求的无时区 ISO 秒精度', () => {
    expect(normalizeLocalDateTime('2026-09-01 12:00:00')).toBe('2026-09-01T12:00:00');
    expect(normalizeLocalDateTime('2026-09-01T12:00')).toBe('2026-09-01T12:00:00');
    expect(normalizeLocalDateTime('2026-09-01T12:00:00.123')).toBe('2026-09-01T12:00:00');
    expect(normalizeLocalDateTime('')).toBeNull();
  });

  it('导出单 Excel 按 5000 行拆分 Sheet，不拆成多个下载包', () => {
    expect(EXPORT_MAX_ROWS_PER_SHEET).toBe(5000);
    expect(getExportSheetCount(0)).toBe(0);
    expect(getExportSheetCount(5000)).toBe(1);
    expect(getExportSheetCount(5001)).toBe(2);
    expect(getExportSheetCount(10001)).toBe(3);
  });

  it('未上报实例将上报人和上报时间归一化为 --', () => {
    expect(normalizeAssignment({
      assignmentId: 9,
      branchName: '第一党支部',
      submitterName: null,
      submittedAt: null,
      status: 'UNREPORTED'
    })).toMatchObject({
      assignmentId: 9,
      submitterName: '--',
      submittedAt: '--'
    });
  });

  it('工作台响应区分 assignment status 与当前 submission status', () => {
    expect(normalizeAssignment({
      assignmentId: 10,
      status: 'BRANCH_PENDING',
      submissionStatus: 'BRANCH_APPROVED',
      branchName: '第一党支部',
      submitterName: '张伟',
      submittedAt: '2026-08-31 10:00:00'
    })).toMatchObject({
      assignmentId: 10,
      status: 'BRANCH_APPROVED',
      assignmentStatus: 'BRANCH_PENDING',
      submissionStatus: 'BRANCH_APPROVED'
    });
  });

  it('分页响应兼容 PageResult 和数组形态', () => {
    expect(normalizePageResult({ records: [{ id: 1 }], total: 8 })).toEqual({
      records: [{ id: 1 }],
      total: 8
    });
    expect(normalizePageResult([{ id: 1 }])).toEqual({
      records: [{ id: 1 }],
      total: 1
    });
  });

  it('任务说明只将 http/https 地址渲染为安全链接', () => {
    const parts = linkifyDescription('查看 https://example.com/a，文本 example.com 不转链接');
    expect(parts.filter((part) => part.type === 'link')).toHaveLength(1);
    expect(parts.find((part) => part.type === 'link')).toMatchObject({
      href: 'https://example.com/a',
      target: '_blank',
      rel: 'noreferrer noopener'
    });
  });
});
