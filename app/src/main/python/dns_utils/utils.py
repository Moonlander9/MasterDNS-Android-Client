# MasterDnsVPN
# Author: MasterkinG32
# Github: https://github.com/masterking32
# Year: 2026
from loguru import logger
import sys
from typing import Optional
import secrets
import asyncio
import socket
import struct


async def async_recvfrom(loop, sock: socket.socket, nbytes: int):
    """Backwards compatible async UDP receive for Python < 3.11 with uvloop fallback"""
    if hasattr(loop, "sock_recvfrom") and sys.version_info >= (3, 11):
        try:
            return await loop.sock_recvfrom(sock, nbytes)
        except (AttributeError, NotImplementedError):
            pass

    try:
        return sock.recvfrom(nbytes)
    except BlockingIOError:
        pass

    future = loop.create_future()
    fd = sock.fileno()

    def cb():
        try:
            data, addr = sock.recvfrom(nbytes)
            loop.remove_reader(fd)
            if not future.done():
                future.set_result((data, addr))
        except BlockingIOError:
            pass
        except Exception as e:
            loop.remove_reader(fd)
            if not future.done():
                future.set_exception(e)

    loop.add_reader(fd, cb)
    try:
        return await future
    except asyncio.CancelledError:
        loop.remove_reader(fd)
        raise


async def async_sendto(loop, sock: socket.socket, data: bytes, addr):
    """Backwards compatible async UDP send for Python < 3.11 with uvloop fallback"""

    def _should_ignore(exc: BaseException) -> bool:
        if isinstance(exc, (ConnectionResetError, BrokenPipeError)):
            return True
        if isinstance(exc, OSError):
            if getattr(exc, "winerror", None) in (10054, 10038, 1236):
                return True
            if getattr(exc, "errno", None) in (32, 104, 9):
                return True
        return False

    if hasattr(loop, "sock_sendto"):
        try:
            return await loop.sock_sendto(sock, data, addr)
        except NotImplementedError:
            pass
        except Exception as e:
            if _should_ignore(e):
                return 0
            raise

    try:
        return sock.sendto(data, addr)
    except BlockingIOError:
        pass
    except Exception as e:
        if _should_ignore(e):
            return 0
        raise

    future = loop.create_future()
    fd = sock.fileno()

    def cb():
        try:
            sent = sock.sendto(data, addr)
            try:
                loop.remove_writer(fd)
            except Exception:
                pass
            if not future.done():
                future.set_result(sent)
        except BlockingIOError:
            pass
        except Exception as e:
            try:
                loop.remove_writer(fd)
            except Exception:
                pass

            if _should_ignore(e):
                if not future.done():
                    future.set_result(0)
                return

            if not future.done():
                future.set_exception(e)

    loop.add_writer(fd, cb)
    try:
        return await future
    except asyncio.CancelledError:
        try:
            loop.remove_writer(fd)
        except Exception:
            pass
        raise


def load_text(file_path: str) -> Optional[str]:
    """
    Load and return the contents of a text file, stripped of leading/trailing whitespace.
    Returns None if the file does not exist or error occurs.
    """
    try:
        with open(file_path, "r", encoding="utf-8") as file:
            return file.read().strip()
    except FileNotFoundError:
        return None
    except Exception:
        return None


def save_text(file_path: str, text: str) -> bool:
    """
    Save the given text to a file. Returns True on success, False otherwise.
    """
    try:
        with open(file_path, "w", encoding="utf-8") as file:
            file.write(text)
        return True
    except Exception:
        return False


def get_encrypt_key(method_id: int) -> str:
    """
    Retrieve or generate an encryption key of appropriate length based on method_id.
    method_id: 3 -> 16 chars, 4 -> 24 chars, else 32 chars.
    Returns the key as a hex string.
    """
    if method_id == 3:
        length = 16
    elif method_id == 4:
        length = 24
    else:
        length = 32
    key_path = "encrypt_key.txt"
    random_key = load_text(key_path)
    if not random_key or len(random_key) != length:
        random_key = generate_random_hex_text(length)
        save_text(key_path, random_key)
    return random_key


def generate_random_hex_text(length: int) -> str:
    """
    Generate a random hexadecimal string of the specified length.
    """
    return secrets.token_hex(length // 2)


def build_socks5_address_bytes(host: str, port: int, atyp: int = 0) -> bytes:
    """Serialize a SOCKS5 destination address."""
    if atyp == 0:
        try:
            socket.inet_pton(socket.AF_INET, host)
            atyp = 0x01
        except OSError:
            try:
                socket.inet_pton(socket.AF_INET6, host)
                atyp = 0x04
            except OSError:
                atyp = 0x03

    if atyp == 0x01:
        return bytes([0x01]) + socket.inet_pton(socket.AF_INET, host) + struct.pack(">H", port)
    if atyp == 0x04:
        return bytes([0x04]) + socket.inet_pton(socket.AF_INET6, host) + struct.pack(">H", port)
    if atyp == 0x03:
        raw = host.encode("utf-8")
        if not raw or len(raw) > 255:
            raise ValueError("Invalid SOCKS5 domain name")
        return bytes([0x03, len(raw)]) + raw + struct.pack(">H", port)
    raise ValueError(f"Unsupported SOCKS5 address type: {atyp}")


def parse_socks5_address_bytes(payload: bytes, offset: int = 0):
    """Parse a SOCKS5 address from payload[offset:].

    Returns (atyp, host, port, next_offset, raw_addr_bytes).
    """
    if len(payload) <= offset:
        raise ValueError("Missing SOCKS5 address type")

    atyp = payload[offset]
    cursor = offset + 1

    if atyp == 0x01:
        if len(payload) < cursor + 4 + 2:
            raise ValueError("Truncated SOCKS5 IPv4 address")
        host = socket.inet_ntop(socket.AF_INET, payload[cursor : cursor + 4])
        cursor += 4
    elif atyp == 0x04:
        if len(payload) < cursor + 16 + 2:
            raise ValueError("Truncated SOCKS5 IPv6 address")
        host = socket.inet_ntop(socket.AF_INET6, payload[cursor : cursor + 16])
        cursor += 16
    elif atyp == 0x03:
        if len(payload) <= cursor:
            raise ValueError("Missing SOCKS5 domain length")
        dlen = payload[cursor]
        cursor += 1
        if dlen == 0 or len(payload) < cursor + dlen + 2:
            raise ValueError("Truncated SOCKS5 domain address")
        host = payload[cursor : cursor + dlen].decode("utf-8", errors="ignore")
        cursor += dlen
    else:
        raise ValueError(f"Unsupported SOCKS5 address type: {atyp}")

    port = struct.unpack(">H", payload[cursor : cursor + 2])[0]
    cursor += 2
    return atyp, host, port, cursor, payload[offset:cursor]


def build_socks5_target_payload(cmd: int, address_bytes: bytes) -> bytes:
    """Serialize client->server SOCKS target metadata.

    Format:
      - Legacy CONNECT: [ATYP][ADDR][PORT]
      - Extended:      [0x00][CMD][ATYP][ADDR][PORT]

    The 0x00 prefix makes the extended form unambiguous for mixed old/new peers.
    CONNECT intentionally stays in legacy format so new Android clients remain
    compatible with older deployed MasterDnsVPN servers for TCP traffic.
    """
    if not address_bytes:
        raise ValueError("SOCKS5 target payload requires address bytes")

    cmd = int(cmd) & 0xFF
    if cmd not in (0x01, 0x03, 0x05):
        raise ValueError(f"Unsupported SOCKS5 command: {cmd}")

    if cmd == 0x01:
        return address_bytes

    return b"\x00" + bytes([cmd]) + address_bytes


def parse_socks5_target_payload(payload: bytes):
    """Parse SOCKS target metadata.

    Returns (cmd, atyp, host, port, next_offset, raw_addr_bytes).
    Supports both the legacy CONNECT-only format and the extended format.
    """
    if not payload:
        raise ValueError("Missing SOCKS5 target payload")

    if payload[0] == 0x00:
        if len(payload) < 3:
            raise ValueError("Truncated SOCKS5 target payload")

        cmd = payload[1]
        if cmd not in (0x01, 0x03, 0x05):
            raise ValueError(f"Unsupported SOCKS5 command: {cmd}")

        atyp, host, port, cursor, raw_addr = parse_socks5_address_bytes(payload, 2)
        return cmd, atyp, host, port, cursor, raw_addr

    atyp, host, port, cursor, raw_addr = parse_socks5_address_bytes(payload, 0)
    return 0x01, atyp, host, port, cursor, raw_addr


def build_socks5_udp_tcp_frame(addr_bytes: bytes, payload: bytes) -> bytes:
    """Build a Hev CMD=0x05 UDP-over-TCP frame."""
    header = struct.pack(">HB", len(payload), 3 + len(addr_bytes))
    return header + addr_bytes + payload


async def read_socks5_udp_tcp_frame(reader):
    """Read one Hev CMD=0x05 UDP-over-TCP frame.

    Returns (raw_addr_bytes, payload_bytes).
    """
    prefix = await reader.readexactly(5)
    datlen = struct.unpack(">H", prefix[:2])[0]
    hdrlen = prefix[2]
    if hdrlen < 5:
        raise ValueError("Invalid UDP-over-TCP header length")

    atyp = prefix[3]
    if atyp == 0x01:
        addrlen = 7
    elif atyp == 0x04:
        addrlen = 19
    elif atyp == 0x03:
        addrlen = 4 + prefix[4]
    else:
        raise ValueError(f"Unsupported SOCKS5 address type: {atyp}")

    remaining_addr = addrlen - 2
    addr_bytes = prefix[3:5]
    if remaining_addr > 0:
        addr_bytes += await reader.readexactly(remaining_addr)

    payload = await reader.readexactly(datlen)
    return addr_bytes, payload


def getLogger(
    log_level: str = "DEBUG",
    logFile: str = None,
    max_log_size: int = 1,
    backup_count: int = 3,
    is_server: bool = False,
):
    # ---------------------------------------------#
    # Logging configuration
    LOG_LEVEL = log_level.upper()
    appName = "MasterDnsVPN Server" if is_server else "MasterDnsVPN Client"
    log_format = f"<cyan>[{appName}]</cyan> <green>[{{time:HH:mm:ss}}]</green> <level>[{{level}}]</level> <white><b>{{message}}</b></white>"

    logger.remove()
    logger.add(
        sink=sys.stdout,
        level=LOG_LEVEL,
        format=log_format,
        colorize=True,
    )

    if logFile:
        log_file_format = f"[{appName}] [{{time:HH:mm:ss}}] [{{level}}] {{message}}"

        logger.add(
            logFile,
            level=LOG_LEVEL,
            format=log_file_format,
            rotation=max_log_size * 1024 * 1024,
            retention=backup_count,
            encoding="utf-8",
            colorize=True,
        )

    logger_final = logger.opt(colors=True)
    return logger_final
