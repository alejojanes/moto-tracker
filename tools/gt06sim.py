"""Simulador mínimo de un rastreador GT06 (Concox): login + posiciones GPS.

Uso: python3 tools/gt06sim.py <host> 5023 <imei>
Envía 4 posiciones GPS y una inválida para probar todo el flujo sin la moto.
"""
import socket, struct, sys, time, datetime

def crc_itu(data: bytes) -> int:  # CRC-16/X-25
    crc = 0xFFFF
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = (crc >> 1) ^ 0x8408 if crc & 1 else crc >> 1
    return ~crc & 0xFFFF

serial = 0
def packet(proto: int, content: bytes) -> bytes:
    global serial
    serial += 1
    body = bytes([len(content) + 5, proto]) + content + struct.pack('>H', serial)
    return b'\x78\x78' + body + struct.pack('>H', crc_itu(body)) + b'\r\n'

def login(imei: str) -> bytes:
    return packet(0x01, bytes.fromhex(imei.rjust(16, '0')))

def location(lat, lon, speed_kmh, course, valid=True, ts=None):
    ts = ts or datetime.datetime.now(datetime.timezone.utc)
    date = bytes([ts.year - 2000, ts.month, ts.day, ts.hour, ts.minute, ts.second])
    flags = int(course) & 0x3FF
    if valid: flags |= 0x1000
    if lat >= 0: flags |= 0x0400
    if lon < 0: flags |= 0x0800
    content = (date + bytes([0xC9])  # 12 bytes de info GPS, 9 satélites
               + struct.pack('>I', int(abs(lat) * 1800000)) + struct.pack('>I', int(abs(lon) * 1800000))
               + bytes([int(speed_kmh)]) + struct.pack('>H', flags)
               + struct.pack('>HB', 732, 101) + struct.pack('>H', 1000) + (12345).to_bytes(3, 'big'))
    return packet(0x12, content)

host, port, imei = sys.argv[1], int(sys.argv[2]), sys.argv[3]
points = [(10.4000, -75.5000, 0), (10.4004, -75.5000, 25), (10.4040, -75.5010, 40), (10.4100, -75.5030, 35)]
with socket.create_connection((host, port), timeout=10) as s:
    s.sendall(login(imei)); print('login ack', s.recv(64).hex())
    for i, (lat, lon, spd) in enumerate(points):
        s.sendall(location(lat, lon, spd, 45))
        print('pos', lat, lon, spd)
        time.sleep(2)
    # una posición inválida (sin fix) con coordenadas lejanas
    s.sendall(location(10.5, -75.6, 0, 0, valid=False)); print('pos invalid')
    time.sleep(3)
