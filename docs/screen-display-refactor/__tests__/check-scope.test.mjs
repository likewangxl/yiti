import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
const checker = path.join(repoRoot, 'docs', 'screen-display-refactor', 'check-scope.mjs');

function run(command, args, cwd) {
  const result = spawnSync(command, args, {
    cwd,
    encoding: 'utf8',
    windowsHide: true,
  });
  return {
    ...result,
    output: `${result.stdout ?? ''}${result.stderr ?? ''}`,
  };
}

function git(cwd, args) {
  const result = run('git', args, cwd);
  assert.equal(result.status, 0, `git ${args.join(' ')} failed:\n${result.output}`);
  return result.stdout.trim();
}

function writeFile(root, relativePath, content = 'fixture\n') {
  const target = path.join(root, ...relativePath.split('/'));
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, content, 'utf8');
}

function fixture(manifestOverrides = {}, initialFiles = {}) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'check-scope-'));
  const baselineDir = fs.mkdtempSync(path.join(os.tmpdir(), 'check-scope-evidence-'));
  git(root, ['init', '-q', '-b', 'main']);
  git(root, ['config', 'user.email', 'scope-test@example.invalid']);
  git(root, ['config', 'user.name', 'scope test']);

  for (const [relativePath, content] of Object.entries(initialFiles)) {
    writeFile(root, relativePath, content);
  }
  if (Object.keys(initialFiles).length === 0) {
    writeFile(root, '.fixture', 'fixture\n');
  }
  git(root, ['add', '--all']);
  git(root, ['commit', '-qm', 'fixture baseline']);
  const baselineCommit = git(root, ['rev-parse', 'HEAD']);

  const manifest = {
    version: 1,
    purpose: 'test scope',
    baselineCommit,
    pathSemantics: 'repository relative POSIX paths',
    defaultAction: 'DENY',
    allowedExistingFiles: [],
    allowedNewFilePrefixes: [],
    testOnlyPrefixes: [],
    restrictedFiles: [],
    documentationPrefixes: [],
    deniedPrefixes: [],
    deniedExactFiles: [],
    contentRestrictions: [],
    taskIds: ['S00'],
    outsideScopeIds: [],
    ...manifestOverrides,
    baselineCommit,
  };
  const manifestPath = path.join(root, 'scope-manifest.json');
  fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');

  function check(...args) {
    return run(process.execPath, [checker, '--manifest', manifestPath, '--baseline-dir', baselineDir, ...args], root);
  }

  const initialized = check('--init-baseline');
  assert.equal(initialized.status, 0, `baseline initialization failed:\n${initialized.output}`);
  return { root, baselineDir, manifestPath, check };
}

test('allows a new file under an allowed presentation prefix', () => {
  const f = fixture({ allowedNewFilePrefixes: ['src/presentation/'] });
  writeFile(f.root, 'src/presentation/NewWidget.vue', '<template />\n');

  const result = f.check();

  assert.equal(result.status, 0, result.output);
});

test('rejects an unknown directory by default', () => {
  const f = fixture({ allowedNewFilePrefixes: ['src/presentation/'] });
  writeFile(f.root, 'src/other/NotAllowed.js');

  const result = f.check();

  assert.notEqual(result.status, 0, result.output);
  assert.match(result.output, /DENY|拒绝|不在允许范围/);
});

test('enforces deny over restricted and allow, and restricted files require hunk review', () => {
  const f = fixture({
    allowedExistingFiles: ['screen.vue'],
    allowedNewFilePrefixes: ['src/'],
    restrictedFiles: [{ path: 'screen.vue', rule: 'review each hunk' }],
    deniedExactFiles: ['screen.vue'],
    deniedPrefixes: ['src/deny/'],
  }, { 'screen.vue': 'before\n' });
  writeFile(f.root, 'src/deny/blocked.js', 'blocked\n');
  fs.writeFileSync(path.join(f.root, 'screen.vue'), 'after\n', 'utf8');

  const result = f.check();

  assert.notEqual(result.status, 0, result.output);
  assert.match(result.output, /DENY/);
  assert.doesNotMatch(result.output, /仅本轮经营屏|需逐hunk人工复核/);
});

test('reports a restricted modification with the required manual-review prompt', () => {
  const f = fixture({
    allowedExistingFiles: ['screen.vue'],
    restrictedFiles: [{ path: 'screen.vue', rule: 'review each hunk' }],
  }, { 'screen.vue': 'before\n' });
  fs.writeFileSync(path.join(f.root, 'screen.vue'), 'after\n', 'utf8');

  const result = f.check();

  assert.equal(result.status, 0, result.output);
  assert.match(result.output, /需逐hunk人工复核/);
});

test('rejects deletion of a protected file using its baseline hash record', () => {
  const f = fixture({ deniedPrefixes: ['protected/'] }, { 'protected/important.txt': 'keep\n' });
  fs.rmSync(path.join(f.root, 'protected/important.txt'));

  const result = f.check();

  assert.notEqual(result.status, 0, result.output);
  assert.match(result.output, /protected\/important\.txt/);
  assert.match(result.output, /删除|不存在|DENY/);
});

test('checks both ends of a rename out of a protected path', () => {
  const f = fixture({
    allowedNewFilePrefixes: ['src/presentation/'],
    deniedPrefixes: ['protected/'],
  }, { 'protected/important.txt': 'keep\n' });
  fs.mkdirSync(path.join(f.root, 'src/presentation'), { recursive: true });
  git(f.root, ['mv', 'protected/important.txt', 'src/presentation/renamed.txt']);

  const result = f.check();

  assert.notEqual(result.status, 0, result.output);
  assert.match(result.output, /protected\/important\.txt/);
  assert.match(result.output, /rename|重命名/i);
});

test('checks the destination end of a rename into an unknown directory', () => {
  const f = fixture({ allowedExistingFiles: ['src/presentation/known.txt'] }, {
    'src/presentation/known.txt': 'known\n',
  });
  fs.mkdirSync(path.join(f.root, 'src/unknown'), { recursive: true });
  git(f.root, ['mv', 'src/presentation/known.txt', 'src/unknown/renamed.txt']);

  const result = f.check();

  assert.notEqual(result.status, 0, result.output);
  assert.match(result.output, /src\/unknown\/renamed\.txt/);
});

test('rejects an out-of-scope file introduced in a committed change after baseline', () => {
  const f = fixture({ allowedExistingFiles: ['src/keep.txt'] }, { 'src/keep.txt': 'keep\n' });
  writeFile(f.root, 'backend/changed.txt', 'out of scope\n');
  git(f.root, ['add', '--all']);
  git(f.root, ['commit', '-qm', 'out of scope change']);

  const result = f.check();

  assert.notEqual(result.status, 0, result.output);
  assert.match(result.output, /backend\/changed\.txt/);
  assert.match(result.output, /committed|提交/i);
});

test('does not classify an unchanged startup-time dirty file as this task violation', () => {
  const f = fixture({ deniedPrefixes: ['user-private/'] }, { 'user-private/notes.txt': 'baseline\n' });
  fs.writeFileSync(path.join(f.root, 'user-private/notes.txt'), 'existing user edit\n', 'utf8');
  const initialized = f.check('--init-baseline');
  assert.equal(initialized.status, 0, initialized.output);
  const result = f.check();

  assert.equal(result.status, 0, result.output);
  assert.match(result.output, /启动时已有|pre-existing|startup/i);
});

test('handles NUL-delimited Chinese untracked paths', () => {
  const f = fixture({ allowedNewFilePrefixes: ['src/presentation/'] });
  writeFile(f.root, 'src/presentation/组件/标题-图表.js', 'export default {};\n');

  const result = f.check();

  assert.equal(result.status, 0, result.output);
  assert.match(result.output, /标题-图表|中文|NUL|通过|allow/i);
});
