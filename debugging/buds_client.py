#!/usr/bin/env python3
"""Minimal RFCOMM client for the realme Buds Wireless 5 ANC (protocol in docs/PROTOCOL.md).

By default it only sends read queries (0x0106, 0x0105, 0x010f) and changes nothing. With --send
it can also write the noise-control mode (0x0404) or the EQ preset (0x0406).

    python3 buds_client.py AA:BB:CC:DD:EE:FF             # tries channels 1..10
    python3 buds_client.py AA:BB:CC:DD:EE:FF --channel 1
"""
import argparse
import socket
import struct
import time

# Commands --send may write: noise control and EQ preset. Both are easy to revert.
ALLOWED_SET = {0x0404, 0x0406}

QUERIES = {0x0106: 'estado', 0x0105: 'versión', 0x010f: 'consulta 0x010f'}


def frame(cmd, seq, data=b''):
    body = b'\x00\x00' + struct.pack('<HBH', cmd, seq, len(data)) + data
    return b'\xaa' + bytes([len(body)]) + body


def read_frames(sock, timeout):
    """Return the complete frames that arrive before the timeout."""
    sock.settimeout(timeout)
    buf, out = b'', []
    try:
        while True:
            chunk = sock.recv(512)
            if not chunk:
                break
            buf += chunk
            while len(buf) >= 2 and buf[0] == 0xaa and len(buf) >= buf[1] + 2:
                n = buf[1] + 2
                out.append(buf[:n])
                buf = buf[n:]
            if buf and buf[0] != 0xaa:
                buf = b''
    except socket.timeout:
        pass
    return out


def parse(f):
    cmd, seq, dlen = struct.unpack('<HBH', f[4:9])
    return cmd, seq, f[9:9 + dlen]


def try_channel(mac, ch, seq=1):
    s = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
    try:
        s.settimeout(8)
        s.connect((mac, ch))
    except OSError as e:
        s.close()
        print(f'canal {ch}: no conecta ({e})')
        return None
    time.sleep(0.3)
    s.send(frame(0x0106, seq))
    frames = read_frames(s, 2.0)
    if frames:
        print(f'canal {ch}: responde con protocolo AA')
        return s
    print(f'canal {ch}: conecta pero no responde a 0x0106')
    s.close()
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('mac')
    ap.add_argument('--channel', type=int)
    ap.add_argument('--send', metavar='CMD:DATAHEX',
                    help='envía un ajuste y muestra la respuesta, p. ej. 0x0404:010108 '
                         '(solo 0x0404 reducción de ruido y 0x0406 ecualizador)')
    a = ap.parse_args()

    chans = [a.channel] if a.channel else range(1, 11)
    sock = None
    for ch in chans:
        sock = try_channel(a.mac, ch)
        if sock:
            break
    if not sock:
        print('Ningún canal respondió. ¿Están emparejados y conectados a este equipo?')
        return

    if a.send:
        c, _, d = a.send.partition(':')
        cmd = int(c, 16)
        if cmd not in ALLOWED_SET:
            print(f'comando 0x{cmd:04x} no permitido en este cliente')
            sock.close()
            return
        sock.send(frame(cmd, 0x60, bytes.fromhex(d)))
        for f in read_frames(sock, 2.0):
            c_, s_, d_ = parse(f)
            estado = 'OK' if d_[:1] == bytes([0]) else 'error'
            print(f'respuesta: cmd=0x{c_:04x} seq=0x{s_:02x} datos={d_.hex()} ({estado})')
        sock.close()
        return

    seq = 0x40
    for cmd, name in QUERIES.items():
        seq += 1
        sock.send(frame(cmd, seq))
        for f in read_frames(sock, 1.5):
            c, s_, d = parse(f)
            print(f'{name}: cmd=0x{c:04x} seq=0x{s_:02x} datos={d.hex()}')
            if c == 0x8105 and len(d) > 1:
                print('   texto:', d[1:].decode('ascii', 'replace'))
    sock.close()


if __name__ == '__main__':
    main()
