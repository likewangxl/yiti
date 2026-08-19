import { describe, expect, it } from 'vitest';
import { slaLabel, taskStatusLabel, normalizePhotoGroups } from '../touchViewModel';

describe('touch view model', () => {
  it('使用红黄蓝灯口径并兼容历史GREEN', () => {
    expect(slaLabel('BLUE')).toBe('蓝灯');
    expect(slaLabel('GREEN')).toBe('蓝灯');
    expect(slaLabel('YELLOW')).toBe('黄灯');
    expect(slaLabel('RED')).toBe('红灯');
  });

  it('展示任务状态中文', () => {
    expect(taskStatusLabel('IN_PROGRESS')).toBe('办理中');
    expect(taskStatusLabel('SUCCESS')).toBe('已完成');
  });

  it('解析后端分类照片JSON', () => {
    expect(normalizePhotoGroups('{"keyPerson":["a.jpg"],"doorplate":[],"workplace":[]}').keyPerson)
      .toEqual(['a.jpg']);
    expect(normalizePhotoGroups(null)).toEqual({ keyPerson: [], doorplate: [], workplace: [] });
  });
});
