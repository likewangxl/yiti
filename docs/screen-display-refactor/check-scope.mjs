import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const BASELINE_FILE = 'baseline.json';
const HASH_FILE = 'protected-hashes.json';
const STARTUP_FILE = 'startup-state.json';

export function normalizePath(value) {
  return String(value).replaceAll('\\', '/').replace(/^\.\//, '');
}

function inside(value, prefix) {
  const p = normalizePath(prefix).replace(/\/$/, '');
  const v = normalizePath(value);
  return v === p || v.startsWith(`${p}/`);
}

function runGit(repoRoot, args) {
  const result = spawnSync('git', args, {
    cwd: repoRoot,
    encoding: 'buffer',
    windowsHide: true,
    maxBuffer: 64 * 1024 * 1024,
  });
  if (result.error || result.status !== 0) {
    const detail = result.error?.message ?? result.stderr?.toString('utf8') ?? `exit ${result.status}`;
    throw new Error(`git ${args.join(' ')} failed: ${detail}`);
  }
  return result.stdout;
}

function text(buffer) {
  return buffer.toString('utf8');
}

function nul(buffer) {
  return text(buffer).split('\0').filter(Boolean);
}

export function parseNameStatus(buffer) {
  const tokens = nul(buffer);
  const changes = [];
  for (let i = 0; i < tokens.length;) {
    const status = tokens[i++];
    const code = status[0];
    if (code === 'R' || code === 'C') {
      changes.push({ status, oldPath: normalizePath(tokens[i++]), newPath: normalizePath(tokens[i++]) });
    } else {
      changes.push({ status, path: normalizePath(tokens[i++]) });
    }
  }
  return changes;
}

function gitText(repoRoot, args) {
  return text(runGit(repoRoot, args)).trim();
}

function sha256(repoRoot, relativePath) {
  const absolute = path.join(repoRoot, ...normalizePath(relativePath).split('/'));
  if (!fs.existsSync(absolute) || !fs.statSync(absolute).isFile()) return null;
  return crypto.createHash('sha256').update(fs.readFileSync(absolute)).digest('hex');
}

function exists(repoRoot, relativePath) {
  return fs.existsSync(path.join(repoRoot, ...normalizePath(relativePath).split('/')));
}

function isDenied(manifest, relativePath) {
  const p = normalizePath(relativePath);
  return manifest.deniedExactFiles.includes(p) || manifest.deniedPrefixes.some((prefix) => inside(p, prefix));
}

function restrictedRule(manifest, relativePath) {
  const p = normalizePath(relativePath);
  return (manifest.restrictedFiles ?? []).find((entry) => normalizePath(entry.path) === p);
}

function testFile(relativePath) {
  const base = path.posix.basename(relativePath);
  return relativePath.includes('/__tests__/') || /(?:\.test|\.spec)\.[^.]+$/.test(base)
    || /(?:Test|Tests)\.[^.]+$/.test(base) || /_test\.[^.]+$/.test(base);
}

export function classifyPath(manifest, relativePath, { isNew = false } = {}) {
  const p = normalizePath(relativePath);
  if (isDenied(manifest, p)) return { action: 'DENY', reason: 'deny', path: p };
  const restricted = restrictedRule(manifest, p);
  if (restricted) return { action: 'RESTRICTED', reason: restricted.rule ?? 'restricted file', path: p };
  const testPrefix = (manifest.testOnlyPrefixes ?? []).find((prefix) => inside(p, prefix));
  if (testPrefix && (!isNew || testFile(p))) return { action: 'ALLOW', reason: 'test-only', path: p };
  if (testPrefix && isNew) return { action: 'DENY', reason: 'test-only prefix requires a test file', path: p };
  if ((manifest.allowedExistingFiles ?? []).map(normalizePath).includes(p)) return { action: 'ALLOW', reason: 'existing allow', path: p };
  if ((manifest.allowedNewFilePrefixes ?? []).some((prefix) => inside(p, prefix))) return { action: 'ALLOW', reason: 'new-file prefix', path: p };
  if ((manifest.documentationPrefixes ?? []).some((prefix) => inside(p, prefix))) return { action: 'ALLOW', reason: 'documentation prefix', path: p };
  return { action: 'DENY', reason: 'default deny', path: p };
}

function allManifestArrays(manifest) {
  for (const key of ['allowedExistingFiles', 'allowedNewFilePrefixes', 'testOnlyPrefixes', 'restrictedFiles', 'documentationPrefixes', 'deniedPrefixes', 'deniedExactFiles']) {
    if (!Array.isArray(manifest[key])) manifest[key] = [];
  }
  return manifest;
}

function repoRootFrom(repo) {
  return path.resolve(repo ?? gitText(process.cwd(), ['rev-parse', '--show-toplevel']));
}

function changes(repoRoot, baselineCommit) {
  const committed = parseNameStatus(runGit(repoRoot, ['diff', '--name-status', '-z', '--find-renames', `${baselineCommit}..HEAD`])).map((c) => ({ ...c, source: 'committed' }));
  const staged = parseNameStatus(runGit(repoRoot, ['diff', '--cached', '--name-status', '-z', '--find-renames'])).map((c) => ({ ...c, source: 'staged' }));
  const unstaged = parseNameStatus(runGit(repoRoot, ['diff', '--name-status', '-z', '--find-renames'])).map((c) => ({ ...c, source: 'unstaged' }));
  const untracked = nul(runGit(repoRoot, ['ls-files', '--others', '--exclude-standard', '-z'])).map((p) => ({ status: '??', path: normalizePath(p), source: 'untracked' }));
  return [...committed, ...staged, ...unstaged, ...untracked];
}

function endpoints(change) {
  if (change.oldPath || change.newPath) return [change.oldPath, change.newPath].filter(Boolean);
  return [change.path].filter(Boolean);
}

function startupUnchanged(repoRoot, baseline, relativePath) {
  const entry = baseline.startupEntries?.[relativePath];
  if (!entry) return false;
  return entry.exists === exists(repoRoot, relativePath) && entry.sha256 === sha256(repoRoot, relativePath);
}

function parseRemote(line) {
  const match = line.match(/^(\S+)\s+(\S+)\s+\((fetch|push)\)$/);
  if (!match) return { name: 'unknown', host: 'unknown', repository: 'unknown', direction: 'unknown' };
  try {
    const url = new URL(match[2]);
    return { name: match[1], host: url.host, repository: url.pathname.replace(/^\//, '').replace(/\.git$/, ''), direction: match[3], url: `${url.protocol}//${url.host}${url.pathname}` };
  } catch {
    return { name: match[1], host: 'redacted', repository: 'redacted', direction: match[3] };
  }
}

export function initializeBaseline(repoRoot, manifest, baselineDir) {
  fs.mkdirSync(baselineDir, { recursive: true });
  const baselineCommit = manifest.baselineCommit;
  gitText(repoRoot, ['cat-file', '-e', `${baselineCommit}^{commit}`]);
  const startupChanges = changes(repoRoot, baselineCommit);
  const startupPaths = new Set(startupChanges.flatMap(endpoints));
  const startupEntries = Object.fromEntries([...startupPaths].sort().map((p) => [p, { exists: exists(repoRoot, p), sha256: sha256(repoRoot, p) }]));
  const tracked = nul(runGit(repoRoot, ['ls-tree', '-r', '--name-only', '-z', baselineCommit])).map(normalizePath);
  const protectedFiles = {};
  for (const p of tracked) {
    if (isDenied(manifest, p) || restrictedRule(manifest, p)) {
      protectedFiles[p] = { exists: exists(repoRoot, p), sha256: sha256(repoRoot, p), enforcement: isDenied(manifest, p) ? 'DENY' : 'RESTRICTED' };
    }
  }
  const remote = gitText(repoRoot, ['remote', '-v']).split(/\r?\n/).filter(Boolean).map(parseRemote);
  const baseline = {
    schemaVersion: 1,
    repoRoot,
    manifestPath: path.resolve(manifest.__path),
    baselineCommit,
    headAtBaseline: gitText(repoRoot, ['rev-parse', 'HEAD']),
    branch: gitText(repoRoot, ['branch', '--show-current']),
    remote,
    trackedAtBaseline: tracked,
    startupPaths: [...startupPaths].sort(),
    startupEntries,
    startupChanges,
    protectedHashesPath: path.resolve(baselineDir, HASH_FILE),
    createdAt: new Date().toISOString(),
  };
  fs.writeFileSync(path.join(baselineDir, HASH_FILE), `${JSON.stringify({ schemaVersion: 1, repoRoot, baselineCommit, files: protectedFiles }, null, 2)}\n`, 'utf8');
  fs.writeFileSync(path.join(baselineDir, STARTUP_FILE), `${JSON.stringify(baseline, null, 2)}\n`, 'utf8');
  fs.writeFileSync(path.join(baselineDir, BASELINE_FILE), `${JSON.stringify(baseline, null, 2)}\n`, 'utf8');
  return baseline;
}

function loadBaseline(baselineDir) {
  const file = path.join(baselineDir, BASELINE_FILE);
  if (!fs.existsSync(file)) throw new Error(`baseline not found: ${file}`);
  return JSON.parse(fs.readFileSync(file, 'utf8'));
}

function currentProtectedProblems(repoRoot, baselineDir, baseline) {
  const hashFile = path.join(baselineDir, HASH_FILE);
  if (!fs.existsSync(hashFile)) throw new Error(`protected hash baseline not found: ${hashFile}`);
  const data = JSON.parse(fs.readFileSync(hashFile, 'utf8'));
  const problems = [];
  for (const [p, expected] of Object.entries(data.files ?? {})) {
    const actual = { exists: exists(repoRoot, p), sha256: sha256(repoRoot, p) };
    if (expected.enforcement === 'DENY' && !startupUnchanged(repoRoot, baseline, p)
      && (actual.exists !== expected.exists || actual.sha256 !== expected.sha256)) {
      problems.push(`保护区哈希变化/删除: ${p}`);
    }
  }
  return problems;
}

export function checkScope(repoRoot, manifest, baselineDir) {
  const baseline = loadBaseline(baselineDir);
  const output = [];
  const errors = [];
  const reviews = [];
  const seen = new Set();
  for (const change of changes(repoRoot, baseline.baselineCommit)) {
    for (const p of endpoints(change)) {
      const key = `${change.source}:${change.status}:${p}`;
      if (seen.has(key)) continue;
      seen.add(key);
      if (startupUnchanged(repoRoot, baseline, p)) {
        output.push(`启动时已有脏文件，保留不判越界: ${p}`);
        continue;
      }
      const isNew = !(baseline.trackedAtBaseline ?? []).includes(p);
      const result = classifyPath(manifest, p, { isNew });
      if (result.action === 'DENY') {
        errors.push(`${result.reason === 'default deny' ? '默认拒绝' : 'DENY'}: ${p} [${change.source}${change.status.startsWith('R') ? ', rename endpoint' : ''}]`);
      } else if (result.action === 'RESTRICTED') {
        reviews.push(`需逐hunk人工复核: ${p} (${result.reason})`);
      }
    }
  }
  errors.push(...currentProtectedProblems(repoRoot, baselineDir, baseline));
  for (const line of reviews) output.push(line);
  if (errors.length) {
    output.push(...errors.map((line) => `范围检查失败: ${line}`));
  } else if (!output.length) {
    output.push('范围检查通过: 未发现超出 SCOPE 的变更');
  } else {
    output.push('范围路径/哈希检查通过；受限文件仍需人工逐 hunk 复核');
  }
  return { ok: errors.length === 0, errors, reviews, output };
}

function parseArgs(argv) {
  const args = { manifest: null, baselineDir: null, repo: null, init: false };
  for (let i = 0; i < argv.length; i += 1) {
    const item = argv[i];
    if (item === '--manifest') args.manifest = argv[++i];
    else if (item === '--baseline-dir') args.baselineDir = argv[++i];
    else if (item === '--repo') args.repo = argv[++i];
    else if (item === '--init-baseline') args.init = true;
    else if (item === '--help' || item === '-h') args.help = true;
    else throw new Error(`unknown argument: ${item}`);
  }
  return args;
}

export async function main(argv = process.argv.slice(2)) {
  const args = parseArgs(argv);
  if (args.help) {
    console.log('node check-scope.mjs --manifest SCOPE.json --baseline-dir <external-dir> [--init-baseline]');
    return 0;
  }
  if (!args.manifest || !args.baselineDir) throw new Error('--manifest and --baseline-dir are required');
  const repoRoot = repoRootFrom(args.repo);
  const manifestPath = path.resolve(repoRoot, args.manifest);
  const manifest = allManifestArrays(JSON.parse(fs.readFileSync(manifestPath, 'utf8')));
  manifest.__path = manifestPath;
  const baselineDir = path.resolve(repoRoot, args.baselineDir);
  if (inside(baselineDir, repoRoot)) throw new Error(`baseline-dir must be outside repository: ${baselineDir}`);
  if (args.init) {
    const baseline = initializeBaseline(repoRoot, manifest, baselineDir);
    console.log(`baseline created: ${path.resolve(baselineDir, BASELINE_FILE)}`);
    console.log(`protected hashes: ${baseline.protectedHashesPath}`);
    return 0;
  }
  const result = checkScope(repoRoot, manifest, baselineDir);
  console.log(result.output.join('\n'));
  return result.ok ? 0 : 1;
}

const invoked = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (invoked) {
  main().then((code) => { process.exitCode = code; }).catch((error) => {
    console.error(`范围检查器错误: ${error.message}`);
    process.exitCode = 2;
  });
}
