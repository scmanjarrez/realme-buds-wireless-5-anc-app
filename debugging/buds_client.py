#!/usr/bin/env python3
"""Minimal RFCOMM client for the realme Buds Wireless 5 ANC.

The protocol is described in docs/PROTOCOL.md. By default the client only
sends read queries (0x0106, 0x0105, 0x010f) and changes nothing. With
--send it can also write the noise-control mode (0x0404) or the EQ preset
(0x0406).

Usage:
    python3 buds_client.py AA:BB:CC:DD:EE:FF
    python3 buds_client.py AA:BB:CC:DD:EE:FF --channel 1
"""

import argparse
import contextlib
import socket
import struct
import sys
import time

# Commands --send may write: noise control and EQ preset. Both are easy to
# revert.
ALLOWED_SET = {0x0404, 0x0406}

QUERIES = {0x0106: "status", 0x0105: "version", 0x010F: "query 0x010f"}

FRAME_SYNC = 0xAA
FRAME_HEADER_LEN = 2
TEXT_RESPONSE = 0x8105


class Args(argparse.Namespace):
    """Command-line arguments accepted by the client.

    Attributes:
        mac: Bluetooth address of the earbuds.
        channel: RFCOMM channel to use, or None to try channels 1 to 10.
        send: Optional 'CMD:DATAHEX' setting to write, or None to query.
    """

    mac: str
    channel: int | None
    send: str | None


def frame(cmd: int, seq: int, data: bytes = b"") -> bytes:
    """Build a request frame for the earbuds.

    The frame starts with a sync byte (0xAA) and a length byte that counts
    the body. The body holds two zero bytes, the command id (uint16, little
    endian), the sequence number (uint8), the data length (uint16, little
    endian) and the data. See the 'Frame anatomy' section of
    docs/PROTOCOL.md for the full layout.

    Args:
        cmd: Command id, for example 0x0106.
        seq: Sequence number that the earbuds echo in their response.
        data: Optional payload bytes.

    Returns:
        The complete frame ready to be sent.
    """
    body = b"\x00\x00" + struct.pack("<HBH", cmd, seq, len(data)) + data
    return bytes([FRAME_SYNC, len(body)]) + body


def read_frames(sock: socket.socket, timeout: float) -> list[bytes]:
    """Read from the socket and return the complete frames received.

    Each frame is split using its length byte, the second byte of the frame
    (see 'Frame anatomy' in docs/PROTOCOL.md). If the buffer does not start
    with the sync byte, the buffered bytes are discarded. Reading stops when
    the timeout expires or the peer closes the connection.

    Args:
        sock: Connected RFCOMM socket.
        timeout: Seconds to wait for data before returning.

    Returns:
        The complete frames that arrived before the timeout.
    """
    sock.settimeout(timeout)
    buf = b""
    out: list[bytes] = []
    with contextlib.suppress(TimeoutError):
        while True:
            chunk = sock.recv(512)
            if not chunk:
                break
            buf += chunk
            while (
                len(buf) >= FRAME_HEADER_LEN
                and buf[0] == FRAME_SYNC
                and len(buf) >= buf[1] + FRAME_HEADER_LEN
            ):
                n = buf[1] + FRAME_HEADER_LEN
                out.append(buf[:n])
                buf = buf[n:]
            if buf and buf[0] != FRAME_SYNC:
                buf = b""
    return out


def parse(f: bytes) -> tuple[int, int, bytes]:
    """Split a complete frame into its command, sequence and data.

    Args:
        f: A complete frame as returned by read_frames.

    Returns:
        A tuple with the command id, the sequence number and the data bytes.
    """
    fields: tuple[int, int, int] = struct.unpack("<HBH", f[4:9])
    cmd, seq, dlen = fields
    return cmd, seq, f[9 : 9 + dlen]


def try_channel(mac: str, ch: int, seq: int = 1) -> socket.socket | None:
    """Open an RFCOMM channel and check that it speaks the AA protocol.

    Sends a 0x0106 probe and keeps the socket open only if a frame comes
    back. A line describing the result is printed for each channel.

    Args:
        mac: Bluetooth address of the earbuds, for example AA:BB:CC:DD:EE:FF.
        ch: RFCOMM channel number to try.
        seq: Sequence number for the probe frame.

    Returns:
        The connected socket if the channel responds, otherwise None.
    """
    s = socket.socket(
        socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM
    )
    try:
        s.settimeout(8)
        s.connect((mac, ch))
    except OSError as e:
        s.close()
        sys.stdout.write(f"channel {ch}: cannot connect ({e})\n")
        return None
    time.sleep(0.3)
    s.send(frame(0x0106, seq))
    frames = read_frames(s, 2.0)
    if frames:
        sys.stdout.write(f"channel {ch}: responds with AA protocol\n")
        return s
    sys.stdout.write(
        f"channel {ch}: connects but does not respond to 0x0106\n"
    )
    s.close()
    return None


def open_socket(mac: str, channel: int | None) -> socket.socket | None:
    """Return the first RFCOMM socket that answers the AA protocol.

    Args:
        mac: Bluetooth address of the earbuds.
        channel: A single channel to try, or None to try channels 1 to 10.

    Returns:
        The connected socket, or None if no channel responded.
    """
    chans = [channel] if channel else list(range(1, 11))
    for ch in chans:
        sock = try_channel(mac, ch)
        if sock:
            return sock
    return None


def send_setting(sock: socket.socket, spec: str) -> None:
    """Write a setting to the earbuds and print each response frame.

    Args:
        sock: Connected RFCOMM socket.
        spec: Setting as 'CMD:DATAHEX', for example '0x0404:010108'.
    """
    c, _, d = spec.partition(":")
    cmd = int(c, 16)
    if cmd not in ALLOWED_SET:
        sys.stdout.write(f"command 0x{cmd:04x} not allowed in this client\n")
        sock.close()
        return
    sock.send(frame(cmd, 0x60, bytes.fromhex(d)))
    for f in read_frames(sock, 2.0):
        c_, s_, d_ = parse(f)
        status = "OK" if d_[:1] == bytes([0]) else "error"
        sys.stdout.write(
            f"response: cmd=0x{c_:04x} seq=0x{s_:02x} "
            f"data={d_.hex()} ({status})\n"
        )
    sock.close()


def run_queries(sock: socket.socket) -> None:
    """Send the read queries and print every response frame.

    Args:
        sock: Connected RFCOMM socket.
    """
    seq = 0x40
    for cmd, name in QUERIES.items():
        seq += 1
        sock.send(frame(cmd, seq))
        for f in read_frames(sock, 1.5):
            c, s_, d = parse(f)
            sys.stdout.write(
                f"{name}: cmd=0x{c:04x} seq=0x{s_:02x} data={d.hex()}\n"
            )
            if c == TEXT_RESPONSE and len(d) > 1:
                text = d[1:].decode("ascii", "replace")
                sys.stdout.write(f"   text: {text}\n")
    sock.close()


def main() -> None:
    """Parse the command line and run queries or a setting write."""
    ap = argparse.ArgumentParser()
    ap.add_argument("mac")
    ap.add_argument("--channel", type=int)
    ap.add_argument(
        "--send",
        metavar="CMD:DATAHEX",
        help="send a setting and show the response, for example "
        "0x0404:010108 (only 0x0404 noise control and 0x0406 equalizer)",
    )
    a = ap.parse_args(namespace=Args())

    sock = open_socket(a.mac, a.channel)
    if not sock:
        sys.stdout.write(
            "No channel responded. Are they paired and connected to this "
            "device?\n"
        )
        return

    if a.send:
        send_setting(sock, a.send)
    else:
        run_queries(sock)


if __name__ == "__main__":
    main()
