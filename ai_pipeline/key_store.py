"""Separate, owner-only mapping storage for the local synthetic MVP."""

import json
import os
import stat
import uuid
from pathlib import Path


class RestrictedFileKeyStore:
    """Store mappings outside the repository on macOS/Linux.

    Directory permissions: 700 (owner only).
    File permissions: 600 (owner read/write only).

    Files are not encrypted. Use only synthetic data for this MVP.
    """

    def __init__(self, directory=None):
        if os.name != "posix":
            raise RuntimeError("UNSUPPORTED_STORAGE_PLATFORM")

        if directory is None:
            directory = Path.home() / "vcnity_story13_private_keys"

        self.directory = Path(directory).expanduser().absolute()

        # Reject symlink paths, including symlinked parent directories.
        for part in (self.directory, *self.directory.parents):
            if part.is_symlink():
                raise RuntimeError("SYMLINK_STORAGE_PATH_REFUSED")

        # Create only the final directory; its parent must already exist.
        try:
            self.directory.mkdir(mode=0o700)
        except FileExistsError:
            pass

        self._check_directory()

    def _check_directory(self):
        info = self.directory.lstat()

        if not stat.S_ISDIR(info.st_mode):
            raise RuntimeError("INVALID_STORAGE_DIRECTORY")

        if info.st_uid != os.getuid():
            raise RuntimeError("STORAGE_OWNER_MISMATCH")

        if stat.S_IMODE(info.st_mode) != 0o700:
            raise RuntimeError("STORAGE_PERMISSIONS_NOT_PRIVATE")

    def save(self, transcript_id, mapping):
        """Return a random reference only after a successful write."""

        self._check_directory()

        key_ref = uuid.uuid4().hex
        filename = f"{key_ref}.json"

        payload = json.dumps(
            {
                "transcript_id": transcript_id,
                "mapping": mapping,
            },
            ensure_ascii=False,
            indent=2,
        )

        # Open the directory itself to anchor file operations.
        directory_fd = os.open(
            self.directory,
            os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW,
        )

        created = False

        try:
            info = os.fstat(directory_fd)
            if (
                info.st_uid != os.getuid()
                or stat.S_IMODE(info.st_mode) != 0o700
            ):
                raise RuntimeError("STORAGE_PERMISSIONS_NOT_PRIVATE")

            file_fd = os.open(
                filename,
                os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                0o600,
                dir_fd=directory_fd,
            )
            created = True

            # fdopen takes ownership of the file descriptor.
            with os.fdopen(file_fd, "w", encoding="utf-8") as handle:
                os.fchmod(handle.fileno(), 0o600)
                handle.write(payload)
                handle.flush()
                os.fsync(handle.fileno())

            os.fsync(directory_fd)

        except Exception:
            if created:
                try:
                    os.unlink(filename, dir_fd=directory_fd)
                except OSError:
                    pass
            raise RuntimeError("KEY_STORAGE_FAILED") from None

        finally:
            os.close(directory_fd)

        return key_ref