#!/usr/bin/env python3
"""
Build the compact offline location DB from the DB-IP City Lite CSV.

Source : https://download.db-ip.com/free/dbip-city-lite-<YYYY-MM>.csv.gz  (CC BY 4.0)
Output : a gzipped LDB1 binary (see location_db.py for the format).

Usage:
  python3 build_location_db.py --out app/src/main/assets/geo/location-db.bin.gz
  python3 build_location_db.py --csv /path/to/dbip-city-lite.csv.gz --out out.bin.gz

By default the current month's CSV is downloaded (and cached) if --csv is not given.
Both IPv4 and IPv6 are ingested at city granularity.

Attribution: this product includes IP geolocation data created by DB-IP.com,
available from https://db-ip.com, licensed under CC BY 4.0.
"""

from __future__ import annotations

import argparse
import csv
import datetime
import gzip
import io
import ipaddress
import sys
import urllib.request
from pathlib import Path

from location_db import LocationDbBuilder, LocationDbReader

DBIP_URL = "https://download.db-ip.com/free/dbip-city-lite-{month}.csv.gz"
DOWNLOAD_TIMEOUT_SECONDS = 60


def _default_month() -> str:
    return datetime.date.today().strftime("%Y-%m")


def _download(month: str, dest: Path) -> Path:
    url = DBIP_URL.format(month=month)
    if dest.exists():
        if dest.stat().st_size == 0:
            print(f"removing empty cached file {dest}")
            dest.unlink()
        else:
            print(f"using cached {dest} ({dest.stat().st_size:,} bytes)")
            return dest
    if dest.exists():
        print(f"using cached {dest} ({dest.stat().st_size:,} bytes)")
        return dest
    print(f"downloading {url} ...")
    dest.parent.mkdir(parents=True, exist_ok=True)
    # DB-IP rejects the default urllib user-agent (403), so present a browser-like one.
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (location-db build)"})
    with urllib.request.urlopen(req, timeout=DOWNLOAD_TIMEOUT_SECONDS) as resp, open(dest, "wb") as f:  # noqa: S310
        f.write(resp.read())
    print(f"saved {dest} ({dest.stat().st_size:,} bytes)")
    return dest


def _empty_db() -> bytes:
    print("building empty location DB fallback")
    return LocationDbBuilder().serialize()


def _open_csv(path: Path) -> io.TextIOBase:
    if path.suffix == ".gz":
        return io.TextIOWrapper(gzip.open(path, "rb"), encoding="utf-8", newline="")
    return open(path, "r", encoding="utf-8", newline="")


def build(csv_path: Path) -> bytes:
    builder = LocationDbBuilder()
    v4 = v6 = 0
    with _open_csv(csv_path) as fh:
        for row in csv.reader(fh):
            if len(row) < 6:
                continue
            start, end, country, city = row[0], row[1], row[3], row[5]
            if ":" in start:
                try:
                    s = int(ipaddress.IPv6Address(start))
                    e = int(ipaddress.IPv6Address(end))
                except ipaddress.AddressValueError:
                    continue
                builder.add_ipv6_range(s, e, country, city)
                v6 += 1
            else:
                try:
                    builder.add_ipv4_range(_ipv4_int(start), _ipv4_int(end), country, city)
                except ValueError:
                    continue
                v4 += 1
    print(f"parsed {v4:,} IPv4 rows + {v6:,} IPv6 rows (both city-level)")
    return builder.serialize()


def _ipv4_int(s: str) -> int:
    a, b, c, d = (int(p) for p in s.split("."))
    if not all(0 <= x <= 255 for x in (a, b, c, d)):
        raise ValueError(s)
    return (a << 24) | (b << 16) | (c << 8) | d


def _report(blob: bytes, gz: bytes) -> None:
    r = LocationDbReader(blob)
    print("\n--- compact DB ---")
    print(f"countries     : {len(r._codes):,}")
    print(f"cities        : {len(r._cities):,}")
    print(f"locations     : {len(r._locs):,}")
    print(f"ipv4 ranges   : {len(r._starts):,}")
    print(f"ipv6 ranges   : {len(r._v6_starts):,}")
    print(f"raw bytes     : {len(blob):,} ({len(blob) / 1e6:.1f} MB)")
    print(f"gzipped bytes : {len(gz):,} ({len(gz) / 1e6:.1f} MB)")
    print("\nsample lookups:")
    for ip in ("1.1.1.1", "8.8.8.8", "208.67.222.222"):
        code, city = r.lookup_ipv4(_ipv4_int(ip))
        print(f"  {ip:<18} -> {code or '??'} / {city or '(no city)'}")
    for ip in ("2606:4700:4700::1111", "2001:4860:4860::8888"):
        code, city = r.lookup_ipv6(int(ipaddress.IPv6Address(ip)))
        print(f"  {ip:<18} -> {code or '??'} / {city or '(no city)'}")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--csv", type=Path, help="path to dbip-city-lite CSV(.gz); downloads if omitted")
    ap.add_argument("--month", default=_default_month(), help="DB-IP month YYYY-MM (download)")
    ap.add_argument("--out", type=Path, required=True, help="output .bin.gz path")
    ap.add_argument("--cache-dir", type=Path, default=Path("/tmp/dbip-cache"))
    ap.add_argument(
        "--offline-empty",
        action="store_true",
        help="skip network access and generate a valid empty LDB1 database",
    )
    args = ap.parse_args()

    if args.offline_empty:
        blob = _empty_db()
    elif args.csv:
        blob = build(args.csv)
    else:
        try:
            csv_path = _download(args.month, args.cache_dir / f"dbip-city-lite-{args.month}.csv.gz")
            blob = build(csv_path)
        except Exception as exc:  # noqa: BLE001 - build must remain offline-capable
            print(f"warning: failed to download/build DB-IP data: {exc}", file=sys.stderr)
            blob = _empty_db()
    gz = gzip.compress(blob, mtime=0)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_bytes(gz)
    _report(blob, gz)
    print(f"\nwrote {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
