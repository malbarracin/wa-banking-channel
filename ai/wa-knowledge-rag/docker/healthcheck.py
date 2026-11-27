"""Container probe: 200 (ready) or 503 (degraded without release) count as alive."""

from __future__ import annotations

import os
import sys
import urllib.error
import urllib.request


def main() -> int:
    port = os.environ.get("KB_PORT", "8102")
    url = f"http://127.0.0.1:{port}/health"
    try:
        with urllib.request.urlopen(url, timeout=4) as response:  # noqa: S310 - localhost probe
            return 0 if response.status in {200, 503} else 1
    except urllib.error.HTTPError as error:
        return 0 if error.code in {200, 503} else 1
    except OSError:
        return 1


if __name__ == "__main__":
    sys.exit(main())
