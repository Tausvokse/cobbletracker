#!/usr/bin/env python3
"""Minimal Minecraft RCON client. Usage: rcon.py <host> <port> <password> "<command>" """
import socket, struct, sys

def pkt(req_id, typ, body):
    data = struct.pack('<ii', req_id, typ) + body.encode('utf-8') + b'\x00\x00'
    return struct.pack('<i', len(data)) + data

def recv_pkt(s):
    raw_len = s.recv(4)
    if len(raw_len) < 4:
        return None, None, ''
    (length,) = struct.unpack('<i', raw_len)
    data = b''
    while len(data) < length:
        chunk = s.recv(length - len(data))
        if not chunk:
            break
        data += chunk
    req_id, typ = struct.unpack('<ii', data[:8])
    body = data[8:-2].decode('utf-8', errors='replace')
    return req_id, typ, body

def main():
    host, port, password, command = sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4]
    s = socket.create_connection((host, port), timeout=10)
    s.sendall(pkt(1, 3, password))          # auth
    rid, typ, body = recv_pkt(s)
    if rid == -1:
        print('AUTH FAILED'); sys.exit(1)
    s.sendall(pkt(2, 2, command))           # command
    rid, typ, body = recv_pkt(s)
    print(body.strip() if body.strip() else '(no output)')
    s.close()

if __name__ == '__main__':
    main()
