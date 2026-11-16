"""Authenticate and unpack offline recovery materials; never execute them or connect to a database."""
import argparse
import base64
import io
import json
from pathlib import Path, PurePosixPath
import shutil
import stat
import tempfile
import zipfile

MAGIC = b'YITI-RECOVERY-1\n'

def restore_bundle(bundle, key_file, output):
    """Reject unauthenticated archives and unsafe paths before creating an output directory."""
    try:
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
        from cryptography.exceptions import InvalidTag
    except ImportError as exc:
        raise ValueError('Install dependency: python -m pip install cryptography') from exc
    output = Path(output).absolute()
    if output.exists() or output.is_symlink():
        raise ValueError('Output must not already exist')
    data = Path(bundle).read_bytes()
    if not data.startswith(MAGIC) or len(data) < len(MAGIC) + 28:
        raise ValueError('Unsupported bundle format')
    try:
        key = base64.b64decode(json.loads(Path(key_file).read_text(encoding='utf-8'))['keyBase64'], validate=True)
        if len(key) != 32: raise ValueError('Expected AES-256 key')
        plain = AESGCM(key).decrypt(data[len(MAGIC):len(MAGIC)+12], data[len(MAGIC)+12:], MAGIC)
    except (InvalidTag, KeyError, ValueError) as exc:
        raise ValueError('Invalid key or damaged bundle; nothing extracted') from exc
    with zipfile.ZipFile(io.BytesIO(plain)) as archive:
        names = set()
        members = archive.infolist()
        if sum(m.file_size for m in members) > 512 * 1024 * 1024:
            raise ValueError('Archive exceeds extraction limit')
        for member in members:
            # ZipInfo normalizes backslashes on Windows; validate the original name as well.
            name = member.orig_filename
            path = PurePosixPath(name)
            if (not name or path.is_absolute() or '\\' in name or ':' in name
                    or any(p in ('..', '.') for p in name.rstrip('/').split('/'))
                    or '' in name.rstrip('/').split('/')
                    or any(p.endswith((' ', '.')) for p in path.parts)
                    or name.rstrip('/').casefold() in names
                    or stat.S_ISLNK(member.external_attr >> 16)):
                raise ValueError('Unsafe or duplicate archive entry')
            names.add(name.rstrip('/').casefold())
        output.parent.mkdir(parents=True, exist_ok=True)
        stage = Path(tempfile.mkdtemp(prefix='.screen-recovery-', dir=output.parent))
        try:
            for member in members:
                destination = stage.joinpath(*PurePosixPath(member.filename).parts)
                if member.is_dir():destination.mkdir(parents=True, exist_ok=True)
                else:
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    with destination.open('xb') as stream:stream.write(archive.read(member))
            if output.exists() or output.is_symlink():raise ValueError('Output appeared during extraction')
            stage.rename(output)
        finally:
            if stage.exists():shutil.rmtree(stage)
    return sum(not m.is_dir() for m in members)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bundle',required=True);parser.add_argument('--key-file',required=True);parser.add_argument('--output',required=True)
    args=parser.parse_args()
    try:count=restore_bundle(args.bundle,args.key_file,args.output)
    except (ValueError,OSError,zipfile.BadZipFile) as exc:parser.exit(1, str(exc)+'\n')
    print(f'Authenticated and extracted {count} files. No scripts executed; no database accessed.')

if __name__=='__main__':main()
