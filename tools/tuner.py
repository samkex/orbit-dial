#!/usr/bin/env python3
"""Drives the dial on a connected phone over HTTP, for live tuning.

    python3 tools/tuner.py                 # picks the only attached (4a) Pro
    python3 tools/tuner.py --serial 003…   # when more than one device is attached

Then POST to it. Anything that can send JSON will do; a page with five sliders is the obvious
client. Each change reaches the LEDs within a frame.

Why this exists: a brightness value that looks right on a screen does not survive the panel, and
without this each candidate value costs an edit, a build and an install.

How it works, and the one part that is not obvious:

    client  --POST /set-->  this server  --adb-->  TuneReceiver  --prefs-->  the service

The broadcast **must name the component explicitly**. A manifest-declared receiver does not get
implicit broadcasts on modern Android, so `am broadcast -a com.kexsam.orbitdial.TUNE` completes with
`result=0` and silently does nothing; `-n com.kexsam.orbitdial/.TuneReceiver` is what makes it arrive.

The receiver is in the debug manifest only, so this cannot touch a release build.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

COMPONENT = "com.kexsam.orbitdial/.TuneReceiver"
ACTION = "com.kexsam.orbitdial.TUNE"
PORT = 8732

# name -> (adb extra flag, python type). Send whichever of these you want to change.
FIELDS = {
    "full": ("--ei", int),
    "dim": ("--ei", int),
    "scale_length": ("--ei", int),
    "minute_size": ("--ei", int),
    "minute_orbit": ("--ef", float),
}

SERIAL = None


def adb(*args: str) -> subprocess.CompletedProcess:
    cmd = ["adb"] + (["-s", SERIAL] if SERIAL else []) + list(args)
    return subprocess.run(cmd, capture_output=True, text=True, timeout=15)


def find_device(wanted: str | None) -> str:
    out = adb("devices", "-l").stdout
    rows = [l.split()[0] for l in out.splitlines()[1:] if "\tdevice" in l or " device " in l]
    if wanted:
        if wanted not in rows:
            sys.exit(f"{wanted} is not attached. Attached: {rows or 'none'}")
        return wanted
    if not rows:
        sys.exit("no device attached")
    # Prefer a (4a) Pro, since that is the only device this toy is for.
    pros = [s for s in rows
            if subprocess.run(["adb", "-s", s, "shell", "getprop", "ro.product.model"],
                              capture_output=True, text=True).stdout.strip() == "A069P"]
    if len(pros) == 1:
        return pros[0]
    if len(rows) == 1:
        return rows[0]
    sys.exit(f"more than one device attached and no (4a) Pro to choose: {rows}. Pass --serial.")


def push(values: dict) -> dict:
    args = ["shell", "am", "broadcast", "-n", COMPONENT, "-a", ACTION]
    sent = {}
    for name, (flag, cast) in FIELDS.items():
        if name in values:
            sent[name] = cast(values[name])
            args += [flag, name, str(sent[name])]
    if not sent:
        return {"ok": False, "error": "nothing to send"}
    r = adb(*args)
    ok = "Broadcast completed" in r.stdout
    return {"ok": ok, "sent": sent, "adb": (r.stdout + r.stderr).strip().splitlines()[-1:]}


class Handler(BaseHTTPRequestHandler):
    def _cors(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Access-Control-Allow-Methods", "POST, GET, OPTIONS")

    def do_OPTIONS(self):
        self.send_response(204)
        self._cors()
        self.end_headers()

    def do_GET(self):
        body = json.dumps({"ok": True, "serial": SERIAL, "fields": list(FIELDS)}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self._cors()
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        n = int(self.headers.get("Content-Length", 0))
        try:
            values = json.loads(self.rfile.read(n) or b"{}")
            result = push(values)
        except Exception as error:                       # noqa: BLE001 — report, do not die
            result = {"ok": False, "error": str(error)}
        body = json.dumps(result).encode()
        self.send_response(200 if result.get("ok") else 400)
        self.send_header("Content-Type", "application/json")
        self._cors()
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
        if result.get("ok"):
            print("  " + "  ".join(f"{k}={v}" for k, v in result["sent"].items()), flush=True)
        else:
            print("  ! " + str(result.get("error") or result), flush=True)

    def log_message(self, *_):                            # the default log is one line per request
        pass


def main():
    global SERIAL
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--serial", help="device serial, when more than one is attached")
    ap.add_argument("--port", type=int, default=PORT)
    args = ap.parse_args()

    SERIAL = find_device(args.serial)
    model = adb("shell", "getprop", "ro.product.model").stdout.strip()
    print(f"tuner -> {SERIAL} ({model}) on http://127.0.0.1:{args.port}")
    if model != "A069P":
        print("  note: that is not a (4a) Pro, so the 13x13 numbers will not apply")
    print(f"  POST JSON to http://127.0.0.1:{args.port}/set with any of: {', '.join(FIELDS)}")
    HTTPServer(("127.0.0.1", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
