"""Running the package as a module.

``python -m drawbarline`` and the installed ``drawbarline`` command do exactly
the same thing. The module form exists because it works before the package is
installed anywhere, which is what somebody does the first time they check the
thing out.
"""

from __future__ import annotations

import sys

from drawbarline.cli import run

if __name__ == "__main__":
    sys.exit(run())
