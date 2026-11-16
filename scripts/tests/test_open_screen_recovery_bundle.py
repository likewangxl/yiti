import base64
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from open_screen_recovery_bundle import restore_bundle, MAGIC

class RecoveryTest(unittest.TestCase):
    def test_roundtrip_and_refuse_existing_destination(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); bundle,key=self.fixture(root, [('config.json', b'{"ok":true}')])
            self.assertEqual(restore_bundle(bundle,key,root/'out'),1)
            self.assertEqual((root/'out/config.json').read_bytes(),b'{"ok":true}')
            with self.assertRaises(ValueError): restore_bundle(bundle,key,root/'out')
            self.assertEqual((root/'out/config.json').read_bytes(),b'{"ok":true}')

    def fixture(self,root,entries):
        data=io.BytesIO()
        with zipfile.ZipFile(data,'w') as z:
            for name,value in entries:
                if isinstance(name,str) and '\\' in name:
                    info=zipfile.ZipInfo('safe');info.filename=name;name=info
                z.writestr(name,value)
        key=AESGCM.generate_key(bit_length=256); nonce=b'123456789012'
        bundle=root/'bundle';bundle.write_bytes(MAGIC+nonce+AESGCM(key).encrypt(nonce,data.getvalue(),MAGIC))
        k=root/'key';k.write_text(json.dumps({'keyBase64':base64.b64encode(key).decode()}))
        return bundle,k

    def test_tamper_rejected_before_writing(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); b,k=self.fixture(root,[('good',b'yes')]);raw=bytearray(b.read_bytes());raw[-1]^=1;b.write_bytes(raw)
            with self.assertRaises(ValueError):restore_bundle(b,k,root/'out')
            self.assertFalse((root/'out').exists())

    def test_wrong_key_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);b,k=self.fixture(root,[('good',b'yes')]);k.write_text(json.dumps({'keyBase64':base64.b64encode(b'x'*32).decode()}))
            with self.assertRaises(ValueError):restore_bundle(b,k,root/'out')
            self.assertFalse((root/'out').exists())

    def test_paths_and_duplicates_rejected(self):
        for names in [['../escape'],['/absolute'],['C:/drive'],['a\\escape'],['A','a']]:
            with self.subTest(names=names),tempfile.TemporaryDirectory() as d:
                root=Path(d);b,k=self.fixture(root,[(n,b'x') for n in names])
                with self.assertRaises(ValueError):restore_bundle(b,k,root/'out')
                self.assertFalse((root/'out').exists())

    def test_symlink_rejected(self):
        info=zipfile.ZipInfo('link');info.create_system=3;info.external_attr=0o120777<<16
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);b,k=self.fixture(root,[(info,b'/elsewhere')])
            with self.assertRaises(ValueError):restore_bundle(b,k,root/'out')
            self.assertFalse((root/'out').exists())

if __name__=='__main__':unittest.main()
