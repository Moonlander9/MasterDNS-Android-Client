"""Android runtime bridge for MasterDnsVPN client.

This module preserves the existing Chaquopy bridge API while adapting the
Android app to the refactored upstream MasterDnsVPN client, which now expects
to read ``client_config.toml`` from its runtime directory and no longer exposes
an event callback hook.
"""

from __future__ import annotations

import asyncio
import builtins
import os
import socket
import threading
import time
from collections import deque
from contextlib import contextmanager
from datetime import datetime, timezone
from typing import Deque, Dict, List, Optional

try:
    import tomllib
except ImportError:  # pragma: no cover
    import tomli as tomllib

import client as client_module
from dns_utils import config_loader as config_loader_module
from loguru import logger as loguru_logger

try:
    from java import jclass
except ImportError:  # pragma: no cover
    jclass = None


STATUS_IDLE = "IDLE"
STATUS_STARTING = "STARTING"
STATUS_STOPPING = "STOPPING"
STATUS_ERROR = "ERROR"

_RUNTIME_DIR_NAME = "masterdnsvpn-python-runtime"
_CONFIG_FILENAME = "client_config.toml"
_VALID_ENCRYPTION_METHODS = {1, 2, 3, 4, 5}
_VALID_COMPRESSION_TYPES = {0, 1, 2, 3}
_SOCKET_PROTECTOR = (
    jclass("com.masterdnsvpn.android.VpnSocketProtector") if jclass is not None else None
)


class AndroidClientBridge:
    def __init__(self) -> None:
        self._thread: Optional[threading.Thread] = None
        self._loop: Optional[asyncio.AbstractEventLoop] = None
        self._client = None
        self._lock = threading.RLock()
        self._events: Deque[dict] = deque(maxlen=2000)
        self._running = False
        self._config_path: Optional[str] = None
        self._runtime_dir: Optional[str] = None
        self._started_at: Optional[float] = None

    @staticmethod
    def _utc_now_iso() -> str:
        return datetime.now(timezone.utc).isoformat()

    def _push_event(self, event: dict) -> None:
        payload = dict(event)
        payload.setdefault("timestamp", self._utc_now_iso())
        with self._lock:
            self._events.append(payload)

    def _push_status(
        self,
        status: str,
        message: str = "",
        code: Optional[str] = None,
    ) -> None:
        payload = {
            "type": "status",
            "status": status,
            "message": message,
            "timestamp": self._utc_now_iso(),
        }
        if code:
            payload["code"] = code
        self._push_event(payload)

    @staticmethod
    def _read_config(config_path: str) -> dict:
        with open(config_path, "rb") as handle:
            return tomllib.load(handle)

    def _validate_config_for_android(self, config_path: str) -> dict:
        if not os.path.isfile(config_path):
            raise RuntimeError(f"CONFIG_NOT_FOUND: {config_path}")

        try:
            config = self._read_config(config_path)
        except Exception as exc:
            raise RuntimeError(f"CONFIG_PARSE_FAILED: {exc}") from exc

        protocol = str(config.get("PROTOCOL_TYPE", "SOCKS5")).upper()
        if protocol != "SOCKS5":
            raise RuntimeError("UNSUPPORTED_PROTOCOL_TYPE: Android supports SOCKS5 only")

        method = int(config.get("DATA_ENCRYPTION_METHOD", 1))
        if method not in _VALID_ENCRYPTION_METHODS:
            raise RuntimeError(
                "UNSUPPORTED_ENCRYPTION_METHOD: Android supports DATA_ENCRYPTION_METHOD values 1-5 only"
            )

        listen_ip = str(config.get("LISTEN_IP", "127.0.0.1")).strip()
        if listen_ip != "127.0.0.1":
            raise RuntimeError(
                "UNSUPPORTED_LISTEN_IP: Android requires LISTEN_IP=127.0.0.1"
            )

        try:
            listen_port = int(config.get("LISTEN_PORT", 1080))
        except Exception as exc:
            raise RuntimeError("INVALID_LISTEN_PORT: LISTEN_PORT must be an integer") from exc
        if listen_port < 1 or listen_port > 65535:
            raise RuntimeError("INVALID_LISTEN_PORT: LISTEN_PORT must be between 1 and 65535")

        if not str(config.get("ENCRYPTION_KEY", "")).strip():
            raise RuntimeError("MISSING_ENCRYPTION_KEY: ENCRYPTION_KEY is required")

        for field_name in ("UPLOAD_COMPRESSION_TYPE", "DOWNLOAD_COMPRESSION_TYPE"):
            value = int(config.get(field_name, 0))
            if value not in _VALID_COMPRESSION_TYPES:
                raise RuntimeError(
                    f"INVALID_{field_name}: {field_name} must be one of 0, 1, 2, 3"
                )

        return config

    def _ensure_runtime_dir(self, config_path: str) -> str:
        runtime_root = os.path.join(os.path.dirname(os.path.abspath(config_path)), _RUNTIME_DIR_NAME)
        os.makedirs(runtime_root, exist_ok=True)
        return runtime_root

    def _prepare_runtime_config(self, config_path: str) -> str:
        runtime_dir = self._ensure_runtime_dir(config_path)
        runtime_config_path = os.path.join(runtime_dir, _CONFIG_FILENAME)
        with open(config_path, "rb") as source:
            content = source.read()
        with open(runtime_config_path, "wb") as target:
            target.write(content)
        return runtime_config_path

    @staticmethod
    def _strip_ansi(text: str) -> str:
        result = []
        index = 0
        text_len = len(text)
        while index < text_len:
            char = text[index]
            if char == "\x1b" and index + 1 < text_len and text[index + 1] == "[":
                index += 2
                while index < text_len and text[index] not in "ABCDEFGHJKSTfmnsu":
                    index += 1
                index += 1
                continue
            result.append(char)
            index += 1
        return "".join(result)

    def _log_sink(self, message) -> None:
        record = message.record
        payload = {
            "type": "log",
            "timestamp": str(record["time"]),
            "level": str(record["level"].name),
            "message": self._strip_ansi(str(record["message"])),
        }
        self._push_event(payload)

    @contextmanager
    def _patched_upstream_config(self, runtime_dir: str):
        original_client_get_config_path = client_module.get_config_path
        original_client_load_config = client_module.load_config
        original_loader_get_config_path = config_loader_module.get_config_path
        original_loader_get_app_dir = config_loader_module.get_app_dir
        original_loader_load_config = config_loader_module.load_config
        original_input = builtins.input

        def _runtime_get_app_dir() -> str:
            return runtime_dir

        def _runtime_get_config_path(config_filename: str) -> str:
            return os.path.join(runtime_dir, config_filename)

        def _runtime_load_config(config_filename: str) -> dict:
            resolved_path = _runtime_get_config_path(config_filename)
            if not os.path.isfile(resolved_path):
                return {}
            with open(resolved_path, "rb") as handle:
                return tomllib.load(handle)

        builtins.input = lambda _prompt="": ""
        client_module.get_config_path = _runtime_get_config_path
        client_module.load_config = _runtime_load_config
        config_loader_module.get_app_dir = _runtime_get_app_dir
        config_loader_module.get_config_path = _runtime_get_config_path
        config_loader_module.load_config = _runtime_load_config
        try:
            yield
        finally:
            builtins.input = original_input
            client_module.get_config_path = original_client_get_config_path
            client_module.load_config = original_client_load_config
            config_loader_module.get_app_dir = original_loader_get_app_dir
            config_loader_module.get_config_path = original_loader_get_config_path
            config_loader_module.load_config = original_loader_load_config

    @contextmanager
    def _patched_socket_protection(self):
        if _SOCKET_PROTECTOR is None:
            yield
            return

        original_socket_cls = socket.socket
        bridge = self

        class ProtectedSocket(original_socket_cls):
            def __init__(
                self,
                family=socket.AF_INET,
                type=socket.SOCK_STREAM,
                proto=0,
                fileno=None,
            ):
                super().__init__(family, type, proto, fileno)
                if fileno is not None:
                    return

                if family not in (socket.AF_INET, socket.AF_INET6):
                    return

                try:
                    protected = bool(_SOCKET_PROTECTOR.protectFd(int(self.fileno())))
                except Exception as exc:  # noqa: BLE001
                    bridge._push_event(
                        {
                            "type": "log",
                            "level": "WARN",
                            "message": f"Failed to protect socket from VPN capture: {exc}",
                        }
                    )
                    return

                if not protected:
                    bridge._push_event(
                        {
                            "type": "log",
                            "level": "WARN",
                            "message": "Socket protection unavailable; upstream traffic may loop through the VPN.",
                        }
                    )

        socket.socket = ProtectedSocket
        try:
            yield
        finally:
            socket.socket = original_socket_cls

    def _runtime_main(self, config_path: str) -> None:
        self._push_status(STATUS_STARTING, "Initializing Python runtime.")
        loop = asyncio.new_event_loop()
        log_sink_id = None

        with self._lock:
            self._loop = loop

        asyncio.set_event_loop(loop)

        try:
            runtime_config_path = self._prepare_runtime_config(config_path)
            runtime_dir = os.path.dirname(runtime_config_path)

            with self._patched_upstream_config(runtime_dir):
                with self._patched_socket_protection():
                    client = client_module.MasterDnsVPNClient()

            log_sink_id = loguru_logger.add(
                self._log_sink,
                level=str(client.config.get("LOG_LEVEL", "INFO")).upper(),
                format="{message}",
                colorize=False,
                backtrace=False,
                diagnose=False,
            )

            with self._lock:
                self._client = client
                self._runtime_dir = runtime_dir
                self._running = True
                self._started_at = time.time()

            with self._patched_socket_protection():
                task = loop.create_task(client.start())
                loop.run_until_complete(task)
            if not client.should_stop.is_set():
                self._push_status(
                    STATUS_ERROR,
                    "Python client start loop exited unexpectedly.",
                    code="BRIDGE_RUNTIME_EXITED",
                )

        except BaseException as exc:  # noqa: BLE001
            self._push_status(STATUS_ERROR, str(exc), code="BRIDGE_RUNTIME_ERROR")
        finally:
            if log_sink_id is not None:
                try:
                    loguru_logger.remove(log_sink_id)
                except Exception:
                    pass

            with self._lock:
                self._running = False
                self._client = None
                self._loop = None
                self._runtime_dir = None
                self._started_at = None

            try:
                loop.stop()
            except Exception:
                pass
            try:
                loop.close()
            except Exception:
                pass

            self._push_status(STATUS_IDLE, "Client runtime stopped.")

    def start(self, config_path: str) -> dict:
        with self._lock:
            if self._running:
                return {
                    "ok": False,
                    "code": "ALREADY_RUNNING",
                    "message": "Client is already running.",
                }

        try:
            self._validate_config_for_android(config_path)
        except Exception as exc:  # noqa: BLE001
            self._push_status(STATUS_ERROR, str(exc), code="INVALID_ANDROID_CONFIG")
            return {"ok": False, "code": "INVALID_ANDROID_CONFIG", "message": str(exc)}

        thread = threading.Thread(
            target=self._runtime_main,
            args=(config_path,),
            name="MasterDnsVPN-Android-Bridge",
            daemon=True,
        )

        with self._lock:
            self._thread = thread
            self._config_path = config_path
            self._events.clear()

        thread.start()
        return {
            "ok": True,
            "code": "STARTED",
            "message": "Client start requested.",
            "config_path": config_path,
        }

    def _request_stop(self, reason: str) -> None:
        with self._lock:
            client = self._client
            loop = self._loop

        if client is None:
            return

        def _trigger_stop() -> None:
            try:
                should_stop = getattr(client, "should_stop", None)
                if should_stop is not None and not should_stop.is_set():
                    should_stop.set()

                restart_event = getattr(client, "session_restart_event", None)
                if restart_event is not None and not restart_event.is_set():
                    restart_event.set()
            except Exception as exc:  # noqa: BLE001
                self._push_status(
                    STATUS_ERROR,
                    f"Failed to signal client stop: {exc}",
                    code="BRIDGE_STOP_SIGNAL_FAILED",
                )

        self._push_status(STATUS_STOPPING, reason)
        if loop is not None and loop.is_running():
            loop.call_soon_threadsafe(_trigger_stop)
        else:
            _trigger_stop()

    def stop(self, timeout_seconds: float = 10.0) -> dict:
        with self._lock:
            thread = self._thread
            is_running = self._running

        if not is_running:
            self._push_status(STATUS_IDLE, "Stop requested while client is idle.")
            return {"ok": True, "code": "IDLE", "message": "Client already stopped."}

        self._request_stop("Stopping client runtime.")

        if thread is not None:
            thread.join(timeout=max(0.5, float(timeout_seconds)))

        with self._lock:
            still_running = self._running

        if still_running:
            self._push_status(
                STATUS_ERROR,
                "Timeout while waiting for runtime to stop.",
                code="STOP_TIMEOUT",
            )
            return {
                "ok": False,
                "code": "STOP_TIMEOUT",
                "message": "Timeout while waiting for client runtime to stop.",
            }

        return {"ok": True, "code": "STOPPED", "message": "Client stopped."}

    def is_running(self) -> bool:
        with self._lock:
            return self._running

    def poll_events(self, max_items: int = 200) -> List[dict]:
        items = []
        pull_count = max(1, min(int(max_items), 2000))
        with self._lock:
            for _ in range(pull_count):
                if not self._events:
                    break
                items.append(self._events.popleft())
        return items


_BRIDGE = AndroidClientBridge()


def start_client(config_path: str) -> dict:
    return _BRIDGE.start(config_path)


def stop_client(timeout_seconds: float = 10.0) -> dict:
    return _BRIDGE.stop(timeout_seconds)


def is_client_running() -> bool:
    return _BRIDGE.is_running()


def poll_events(max_items: int = 200) -> List[dict]:
    return _BRIDGE.poll_events(max_items)
