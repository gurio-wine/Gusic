#!/usr/bin/env python3
"""Validate the stable version and derive this CI alpha version."""

from __future__ import annotations

import argparse
import re
import sys
from datetime import date


VERSION_PATTERN = re.compile(
    r"^(?P<year>\d{2})\.(?P<month>\d{2})\.(?P<day>\d{2})(?:\.(?P<mod>\d{3}))?$"
)
MAX_ANDROID_VERSION_CODE = 2**31 - 1


def derive_alpha_version(base_version: str, base_code: int, run_number: int) -> tuple[str, int]:
    match = VERSION_PATTERN.fullmatch(base_version)
    if match is None:
        raise ValueError("APP_VERSION_NAME must use YY.MM.DD or YY.MM.DD.MOD")

    year = int(match.group("year"))
    month = int(match.group("month"))
    day = int(match.group("day"))
    try:
        date(2000 + year, month, day)
    except ValueError as error:
        raise ValueError(f"APP_VERSION_NAME must contain a valid date: {error}") from error

    modification = int(match.group("mod") or "000")
    date_code = year * 10000 + month * 100 + day
    expected_base_code = date_code * 2000 + modification
    if base_code != expected_base_code:
        raise ValueError(
            f"APP_VERSION_CODE {base_code} does not match {base_version} "
            f"(expected {expected_base_code})"
        )

    if not 1 <= run_number <= 999:
        raise ValueError(f"Alpha run number must be between 1 and 999 (got {run_number})")

    alpha_code = date_code * 2000 + 1000 + run_number
    if alpha_code > MAX_ANDROID_VERSION_CODE:
        raise ValueError(f"Alpha version code exceeds Android's limit: {alpha_code}")

    return f"{base_version}-alpha.{run_number}", alpha_code


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-version", required=True)
    parser.add_argument("--base-code", required=True, type=int)
    parser.add_argument("--run-number", required=True, type=int)
    args = parser.parse_args()

    try:
        alpha_version, alpha_code = derive_alpha_version(
            args.base_version,
            args.base_code,
            args.run_number,
        )
    except ValueError as error:
        parser.error(str(error))

    print(f"name={alpha_version}")
    print(f"code={alpha_code}")
    print(f"tag=v{alpha_version}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
