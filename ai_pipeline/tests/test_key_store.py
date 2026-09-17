import json
import os
import stat
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from ai_pipeline.key_store import RestrictedFileKeyStore


@unittest.skipUnless(os.name == "posix", "Requires macOS or Linux")
class KeyStoreTests(unittest.TestCase):

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()

        # Resolve macOS temporary-folder aliases before using the path.
        self.base = Path(self.temporary.name).resolve()
        self.directory = self.base / "restricted_keys"
        self.store = RestrictedFileKeyStore(self.directory)

        self.mapping = [{
            "pseudonym": "PERSON_001",
            "original": "Alice Example",
            "category": "PERSON",
        }]

    def tearDown(self):
        self.temporary.cleanup()

    def test_directory_is_owner_only(self):
        permissions = stat.S_IMODE(self.directory.stat().st_mode)
        self.assertEqual(permissions, 0o700)

    def test_saved_mapping_has_correct_contents_and_permissions(self):
        reference = self.store.save("synthetic_001", self.mapping)
        path = self.directory / f"{reference}.json"

        self.assertTrue(path.is_file())
        self.assertEqual(
            stat.S_IMODE(path.stat().st_mode),
            0o600,
        )

        saved = json.loads(path.read_text(encoding="utf-8"))

        self.assertEqual(saved["transcript_id"], "synthetic_001")
        self.assertEqual(saved["mapping"], self.mapping)
        self.assertRegex(reference, r"^[0-9a-f]{32}$")

    def test_repeated_saves_do_not_overwrite_existing_mapping(self):
        first = self.store.save("synthetic_001", self.mapping)
        second = self.store.save("synthetic_001", self.mapping)

        self.assertNotEqual(first, second)
        self.assertTrue((self.directory / f"{first}.json").is_file())
        self.assertTrue((self.directory / f"{second}.json").is_file())

    def test_insecure_existing_directory_is_refused(self):
        other = self.base / "open_directory"
        other.mkdir()
        other.chmod(0o755)

        with self.assertRaisesRegex(
            RuntimeError,
            "STORAGE_PERMISSIONS_NOT_PRIVATE",
        ):
            RestrictedFileKeyStore(other)

    def test_permission_change_blocks_later_save(self):
        self.directory.chmod(0o755)

        with self.assertRaisesRegex(
            RuntimeError,
            "STORAGE_PERMISSIONS_NOT_PRIVATE",
        ):
            self.store.save("synthetic_001", self.mapping)

        self.assertEqual(list(self.directory.iterdir()), [])

    def test_symlink_directory_is_refused(self):
        link = self.base / "linked_keys"
        link.symlink_to(self.directory, target_is_directory=True)

        with self.assertRaisesRegex(
            RuntimeError,
            "SYMLINK_STORAGE_PATH_REFUSED",
        ):
            RestrictedFileKeyStore(link)

    def test_failed_write_removes_partial_mapping(self):
        with patch(
            "ai_pipeline.key_store.os.fsync",
            side_effect=OSError("Synthetic disk failure"),
        ):
            with self.assertRaisesRegex(
                RuntimeError,
                "^KEY_STORAGE_FAILED$",
            ):
                self.store.save("synthetic_001", self.mapping)

        self.assertEqual(list(self.directory.iterdir()), [])


if __name__ == "__main__":
    unittest.main()