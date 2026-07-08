#!/usr/bin/env python3
"""Pulse Link PC relay for XInput-compatible gamepads.

The Android app can run as the haptic center and fan out /vibrate calls to this
small HTTP bridge. The bridge returns quickly, then plays the waveform on the
first connected XInput controller in a background thread.
"""

from __future__ import annotations

import argparse
import ctypes
import json
import random
import signal
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.parse import parse_qs, urlparse


ERROR_SUCCESS = 0
ERROR_DEVICE_NOT_CONNECTED = 1167
MAX_CONTROLLERS = 4


class XInputGamepad(ctypes.Structure):
    _fields_ = [
        ("wButtons", ctypes.c_ushort),
        ("bLeftTrigger", ctypes.c_ubyte),
        ("bRightTrigger", ctypes.c_ubyte),
        ("sThumbLX", ctypes.c_short),
        ("sThumbLY", ctypes.c_short),
        ("sThumbRX", ctypes.c_short),
        ("sThumbRY", ctypes.c_short),
    ]


class XInputState(ctypes.Structure):
    _fields_ = [
        ("dwPacketNumber", ctypes.c_uint32),
        ("Gamepad", XInputGamepad),
    ]


class XInputVibration(ctypes.Structure):
    _fields_ = [
        ("wLeftMotorSpeed", ctypes.c_ushort),
        ("wRightMotorSpeed", ctypes.c_ushort),
    ]


def load_xinput() -> tuple[Any | None, str | None]:
    for name in ("xinput1_4.dll", "xinput1_3.dll", "xinput9_1_0.dll"):
        try:
            dll = ctypes.WinDLL(name)
            dll.XInputGetState.argtypes = [ctypes.c_uint32, ctypes.POINTER(XInputState)]
            dll.XInputGetState.restype = ctypes.c_uint32
            dll.XInputSetState.argtypes = [ctypes.c_uint32, ctypes.POINTER(XInputVibration)]
            dll.XInputSetState.restype = ctypes.c_uint32
            return dll, name
        except Exception:
            continue
    return None, None


PATTERNS: dict[str, list[list[int]]] = {
    "mode_1": [
        [0, 1000, 5500, 1000, 5500, 1000, 5500, 1000, 5500, 1000],
        [0, 1000, 2500, 1000, 2500, 1000, 2500, 1000, 2500, 1000],
        [0, 1000, 1500, 1000, 1500, 1000, 1500, 1000, 1500, 1000],
        [0, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000],
        [100, 1000, 100, 1000, 100, 1000, 100, 1000, 100, 1000],
    ],
    "mode_2": [
        [0, 500, 1100, 500, 1100, 500, 1100, 500, 1100, 500],
        [0, 500, 600, 500, 600, 500, 600, 500, 600, 500],
        [0, 500, 100, 500, 100, 500, 100, 500, 100, 500],
        [0, 500, 10, 500, 10, 500, 10, 500, 10, 500],
        [0, 500, 3, 500, 3, 500, 3, 500, 3, 500],
    ],
    "mode_3": [
        [0, 120, 600, 120, 600, 120, 600, 120, 600, 120],
        [0, 120, 400, 120, 400, 120, 400, 120, 400, 120],
        [0, 120, 320, 120, 320, 120, 320, 120, 320, 120],
        [0, 120, 200, 120, 200, 120, 200, 120, 200, 120],
        [0, 120, 80, 120, 80, 120, 80, 120, 80, 120],
    ],
    "mode_4": [
        [0, 100, 500, 300, 600, 500, 700, 700, 900, 900],
        [0, 100, 400, 300, 500, 500, 600, 700, 700, 900],
        [0, 100, 300, 300, 400, 500, 500, 700, 600, 900],
        [0, 100, 200, 300, 300, 500, 400, 700, 500, 900],
        [0, 100, 100, 300, 200, 500, 300, 700, 400, 900],
    ],
    "mode_5": [
        [0, 300, 1200, 200, 1000, 100, 800, 1200, 400, 300],
        [0, 300, 1100, 200, 900, 100, 700, 1200, 300, 300],
        [0, 300, 900, 200, 700, 100, 500, 1200, 200, 300],
        [0, 300, 700, 200, 500, 100, 300, 1200, 200, 300],
        [0, 300, 500, 200, 300, 100, 100, 1200, 50, 300],
    ],
    "mode_6": [
        [0, 10, 900, 80, 700, 60, 500, 40, 300, 20],
        [0, 10, 850, 80, 650, 60, 450, 40, 250, 20],
        [0, 10, 800, 80, 600, 60, 400, 40, 200, 20],
        [0, 10, 750, 80, 550, 60, 350, 40, 150, 20],
        [0, 10, 700, 80, 500, 60, 300, 40, 100, 20],
    ],
    "mode_7": [[0, 4000], [0, 4000], [0, 4000], [0, 4000], [0, 4000]],
    "mode_8": [
        [0, 500, 600, 500, 500, 500, 400, 500, 600, 800],
        [0, 500, 500, 500, 400, 500, 300, 500, 500, 800],
        [0, 500, 400, 500, 300, 500, 200, 500, 400, 800],
        [0, 500, 300, 500, 200, 500, 100, 500, 300, 800],
        [0, 500, 200, 500, 100, 500, 50, 500, 200, 800],
    ],
    "mode_9": [
        [0, 100, 200, 100, 200, 100, 200, 100, 1],
        [0, 100, 180, 100, 180, 100, 180, 100, 1],
        [0, 100, 160, 100, 160, 100, 160, 100, 1],
        [0, 100, 140, 100, 140, 100, 140, 100, 1],
        [0, 100, 120, 100, 120, 100, 120, 100, 1],
    ],
    "mode_10": [
        [0, 600, 800, 600, 800, 600, 800, 600, 800, 600],
        [0, 600, 700, 600, 700, 600, 700, 600, 700, 600],
        [0, 600, 600, 600, 600, 600, 600, 600, 600, 600],
        [0, 600, 500, 600, 500, 600, 500, 600, 500, 600],
        [0, 600, 300, 600, 300, 600, 300, 600, 300, 600],
    ],
    "mode_11": [
        [0, 100, 250, 100, 250, 100, 250, 100, 250, 100],
        [0, 100, 230, 100, 230, 100, 230, 100, 230, 100],
        [0, 100, 200, 100, 200, 100, 200, 100, 200, 100],
        [0, 100, 180, 100, 180, 100, 180, 100, 180, 100],
        [0, 100, 150, 100, 150, 100, 150, 100, 150, 100],
    ],
    "mode_12": [
        [0, 1200, 1200, 120, 1000, 1200, 800, 1200, 600, 1200],
        [0, 1200, 1100, 120, 900, 1200, 700, 1200, 500, 1200],
        [0, 1200, 1000, 120, 800, 1200, 600, 1200, 400, 1200],
        [0, 1200, 900, 120, 700, 1200, 500, 1200, 300, 1200],
        [0, 1200, 700, 120, 500, 1200, 300, 1200, 100, 1200],
    ],
    "mode_13": [
        [0, 300, 1550, 300, 1550, 300, 1550, 300, 1550, 300],
        [0, 300, 1050, 300, 1050, 300, 1050, 300, 1050, 300],
        [0, 300, 550, 300, 550, 300, 550, 300, 550, 300],
        [0, 300, 10, 300, 10, 300, 10, 300, 10, 300],
        [0, 300, 1, 300, 1, 300, 1, 300, 1, 300],
    ],
    "mode_14": [
        [0, 500, 1380, 500, 1380, 200, 1380, 60, 1380, 200],
        [0, 500, 1230, 500, 1230, 200, 1230, 60, 1230, 200],
        [0, 500, 780, 500, 780, 200, 780, 60, 780, 200],
        [0, 500, 30, 500, 30, 200, 30, 60, 30, 200],
        [0, 500, 3, 500, 3, 200, 3, 60, 3, 200],
    ],
    "mode_15": [
        [0, 50, 1230, 70, 1230, 90, 1230, 110, 1230, 130],
        [0, 50, 630, 70, 630, 90, 630, 110, 630, 130],
        [0, 50, 330, 70, 330, 90, 330, 110, 330, 130],
        [0, 50, 30, 70, 30, 90, 30, 110, 30, 130],
        [0, 50, 1, 70, 1, 90, 1, 110, 1, 130],
    ],
    "mode_16": [
        [0, 200, 2500, 200, 2500, 200, 2500, 200, 2500, 200],
        [0, 200, 1750, 200, 1750, 200, 1750, 200, 1750, 200],
        [0, 200, 1000, 200, 1000, 200, 1000, 200, 1000, 200],
        [0, 200, 250, 200, 250, 200, 250, 200, 250, 200],
        [0, 200, 30, 200, 30, 200, 30, 200, 30, 200],
    ],
    "mode_17": [
        [0, 100, 1000, 100, 1000, 100, 1000],
        [0, 100, 900, 100, 900, 100, 900],
        [0, 100, 800, 100, 800, 100, 800],
        [0, 100, 700, 100, 700, 100, 700],
        [0, 100, 600, 100, 600, 100, 600],
    ],
    "mode_18": [
        [0, 150, 1850, 150, 1850, 150, 1850, 150, 1850, 150],
        [0, 150, 1350, 150, 1350, 150, 1350, 150, 1350, 150],
        [0, 150, 850, 150, 850, 100, 850, 150, 850, 150],
        [0, 150, 600, 150, 600, 100, 600, 150, 600, 150],
        [0, 150, 100, 150, 100, 150, 100, 150, 100, 150],
    ],
    "mode_19": [
        [0, 150, 3500, 150, 3500, 150, 3500, 150, 3500, 150],
        [0, 150, 1300, 150, 1300, 150, 1300, 150, 1300, 150],
        [0, 150, 700, 150, 700, 150, 700, 150, 700, 150],
        [0, 150, 400, 150, 400, 150, 400, 150, 400, 150],
        [0, 150, 100, 150, 100, 150, 100, 150, 100, 150],
    ],
    "mode_20": [
        [0, 200, 510, 200, 510, 100, 510, 200, 510, 500],
        [0, 200, 360, 200, 360, 100, 360, 200, 360, 500],
        [0, 200, 210, 200, 210, 100, 210, 200, 210, 500],
        [0, 200, 10, 200, 10, 100, 10, 200, 10, 500],
        [0, 200, 1, 200, 1, 100, 1, 200, 1, 500],
    ],
    "mode_21": [
        [0, 1000, 3600, 800, 2800, 600, 2000, 400, 1200, 200],
        [0, 1000, 1800, 800, 1400, 600, 1000, 400, 600, 200],
        [0, 1000, 900, 800, 700, 600, 500, 400, 300, 200],
        [0, 1000, 450, 800, 350, 600, 250, 400, 150, 200],
        [0, 1000, 180, 800, 140, 600, 100, 400, 60, 200],
    ],
    "mode_22": [
        [0, 50, 930, 90, 930, 120, 930, 150, 930, 200],
        [0, 50, 480, 90, 480, 120, 480, 150, 480, 200],
        [0, 50, 330, 90, 330, 120, 330, 150, 330, 200],
        [0, 50, 30, 90, 30, 120, 30, 150, 30, 200],
        [0, 50, 3, 90, 3, 120, 3, 150, 3, 200],
    ],
    "mode_23": [
        [0, 2000, 30, 2000, 30, 2000, 30, 2000, 30, 0],
        [0, 2000, 30, 2000, 30, 2000, 30, 2000, 30, 0],
        [0, 2000, 30, 2000, 30, 2000, 30, 2000, 30, 0],
        [0, 2000, 30, 2000, 30, 2000, 30, 2000, 30, 0],
        [0, 2000, 30, 2000, 30, 2000, 30, 2000, 30, 0],
    ],
}


ALIASES = {
    "gentle": "mode_1",
    "pulse": "mode_2",
    "heartbeat": "mode_3",
    "impact": "mode_5",
    "wave": "mode_10",
}
for i in range(1, 24):
    ALIASES[f"mode_{i}"] = f"mode_{i}"
    ALIASES[f"mode{i}"] = f"mode_{i}"
    ALIASES[str(i)] = f"mode_{i}"


def canonical_mode(raw: str) -> str | None:
    key = str(raw or "").strip().lower().replace(" ", "")
    return ALIASES.get(key)


def coerce_level(value: str | None) -> int:
    try:
        return max(1, min(5, int(value or "3")))
    except ValueError:
        return 3


def motor_speeds(level: int) -> tuple[int, int]:
    base = {1: 22000, 2: 32000, 3: 45000, 4: 56000, 5: 65535}[max(1, min(5, level))]
    right = min(65535, int(base * (0.72 if level < 4 else 0.88)))
    return base, right


def maybe_randomize(timings: list[int], enabled: bool) -> list[int]:
    if not enabled:
        return timings[:]
    randomized: list[int] = []
    for ms in timings:
        if ms <= 0:
            randomized.append(ms)
            continue
        randomized.append(max(1, int(ms * (1.0 + random.uniform(-0.15, 0.15)))))
    return randomized


class XInputBridge:
    def __init__(self) -> None:
        self.dll, self.dll_name = load_xinput()
        self.lock = threading.Lock()
        self.stop_event: threading.Event | None = None
        self.thread: threading.Thread | None = None
        self.active_index: int | None = None
        self.last_command: dict[str, Any] | None = None

    def status(self) -> dict[str, Any]:
        indices = self.connected_indices()
        return {
            "success": self.dll is not None,
            "bridge": "pulselink_pc_xinput",
            "xinput_dll": self.dll_name,
            "controller_connected": bool(indices),
            "controller_indices": indices,
            "active": self.thread is not None and self.thread.is_alive(),
            "last_command": self.last_command,
        }

    def connected_indices(self) -> list[int]:
        if self.dll is None:
            return []
        connected: list[int] = []
        for index in range(MAX_CONTROLLERS):
            state = XInputState()
            result = self.dll.XInputGetState(index, ctypes.byref(state))
            if result == ERROR_SUCCESS:
                connected.append(index)
        return connected

    def first_connected_index(self) -> int | None:
        indices = self.connected_indices()
        return indices[0] if indices else None

    def trigger(self, mode: str, level: int, randomize: bool, target: str) -> dict[str, Any]:
        targets = normalize_targets(target)
        if not targets.intersection({"gamepad", "controller", "all"}):
            return {"success": True, "ignored": True, "target": target, "targets": sorted(targets)}

        mode_id = canonical_mode(mode)
        if mode_id is None or mode_id not in PATTERNS:
            return {"success": False, "error": "unknown_mode", "mode": mode}

        index = self.first_connected_index()
        if index is None:
            return {"success": False, "error": "no_xinput_controller", **self.status()}

        self.stop(join=True)
        stop_event = threading.Event()
        timing = maybe_randomize(PATTERNS[mode_id][level - 1], randomize)
        with self.lock:
            self.stop_event = stop_event
            self.active_index = index
            self.last_command = {
                "mode": mode_id,
                "level": level,
                "randomize": randomize,
                "target": "gamepad",
                "targets": sorted(targets),
                "controller_index": index,
            }
            self.thread = threading.Thread(
                target=self._run_pattern,
                args=(index, mode_id, timing, level, stop_event),
                name="PulseLinkXInputPattern",
                daemon=True,
            )
            self.thread.start()
        return {"success": True, "mode": mode_id, "level": level, "controller_index": index}

    def stop(self, join: bool = False) -> dict[str, Any]:
        with self.lock:
            event = self.stop_event
            thread = self.thread
            self.stop_event = None
            self.thread = None
        if event is not None:
            event.set()
        if join and thread is not None and thread is not threading.current_thread():
            thread.join(timeout=0.2)
        self._set_all_motors(0, 0)
        return {"success": True}

    def _run_pattern(
        self,
        index: int,
        mode_id: str,
        timing: list[int],
        level: int,
        stop_event: threading.Event,
    ) -> None:
        low, high = motor_speeds(level)
        try:
            if mode_id == "mode_7":
                self._set_motors(index, low, high)
                self._sleep_until_stop(stop_event)
                return

            for segment_index, ms in enumerate(timing):
                if stop_event.is_set():
                    return
                if segment_index % 2 == 0:
                    self._set_motors(index, 0, 0)
                else:
                    self._set_motors(index, low, high)
                if not self._sleep_ms(ms, stop_event):
                    return
        finally:
            self._set_motors(index, 0, 0)
            with self.lock:
                if self.stop_event is stop_event:
                    self.stop_event = None
                    self.thread = None

    def _set_all_motors(self, low: int, high: int) -> None:
        for index in self.connected_indices():
            self._set_motors(index, low, high)

    def _set_motors(self, index: int, low: int, high: int) -> bool:
        if self.dll is None:
            return False
        vibration = XInputVibration(max(0, min(65535, low)), max(0, min(65535, high)))
        result = self.dll.XInputSetState(index, ctypes.byref(vibration))
        return result == ERROR_SUCCESS

    @staticmethod
    def _sleep_ms(ms: int, stop_event: threading.Event) -> bool:
        if ms <= 0:
            return not stop_event.is_set()
        return not stop_event.wait(ms / 1000.0)

    @staticmethod
    def _sleep_until_stop(stop_event: threading.Event) -> None:
        while not stop_event.wait(0.25):
            pass


class PulseLinkHandler(BaseHTTPRequestHandler):
    server_version = "PulseLinkPcBridge/1.0"

    def do_OPTIONS(self) -> None:
        self.send_response(204)
        self._send_cors_headers()
        self.end_headers()

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        params = parse_qs(parsed.query)
        bridge: XInputBridge = self.server.bridge  # type: ignore[attr-defined]

        if parsed.path == "/status":
            self._send_json(bridge.status())
            return

        if parsed.path == "/vibrate":
            response = bridge.trigger(
                mode=self._param(params, "mode", "mode_1"),
                level=coerce_level(self._param(params, "level", "3")),
                randomize=self._param(params, "randomize", "true").lower() == "true",
                target=self._param(params, "targets", self._param(params, "target", "gamepad")),
            )
            self._send_json(response)
            return

        if parsed.path == "/stop":
            self._send_json(bridge.stop(join=False))
            return

        self._send_json({"success": False, "error": "not_found", "path": parsed.path}, status=404)

    def log_message(self, format: str, *args: Any) -> None:
        sys.stdout.write("%s - %s\n" % (self.address_string(), format % args))
        sys.stdout.flush()

    def _send_json(self, payload: dict[str, Any], status: int = 200) -> None:
        body = json.dumps(payload, ensure_ascii=True).encode("utf-8")
        self.send_response(status)
        self._send_cors_headers()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_cors_headers(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")

    @staticmethod
    def _param(params: dict[str, list[str]], name: str, default: str) -> str:
        values = params.get(name)
        return values[0] if values else default


def normalize_targets(raw: str) -> set[str]:
    tokens = str(raw or "gamepad").replace(";", ",").replace(" ", ",").split(",")
    targets: set[str] = set()
    for token in (item.strip().lower() for item in tokens):
        if not token:
            continue
        if token == "all":
            targets.update({"all", "gamepad"})
        elif token in {"gamepad", "controller"}:
            targets.add(token)
        elif token in {"phone", "toy", "egg", "fleshlight", "local"}:
            targets.add(token)
    return targets or {"gamepad"}


def main() -> int:
    parser = argparse.ArgumentParser(description="Pulse Link XInput PC bridge")
    parser.add_argument("--host", default="127.0.0.1", help="Bind host, use 0.0.0.0 for LAN access")
    parser.add_argument("--port", default=8081, type=int, help="Bind port")
    args = parser.parse_args()

    bridge = XInputBridge()
    server = ThreadingHTTPServer((args.host, args.port), PulseLinkHandler)
    server.bridge = bridge  # type: ignore[attr-defined]

    def shutdown(_signum: int, _frame: Any) -> None:
        bridge.stop(join=False)
        server.shutdown()

    signal.signal(signal.SIGINT, shutdown)
    signal.signal(signal.SIGTERM, shutdown)

    print(
        json.dumps(
            {
                "bridge": "pulselink_pc_xinput",
                "url": f"http://{args.host}:{args.port}",
                "status": bridge.status(),
            },
            ensure_ascii=True,
        ),
        flush=True,
    )
    server.serve_forever()
    bridge.stop(join=False)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
