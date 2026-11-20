#!/usr/bin/env python3
"""打包 APK（含 classes.dex）并做 4 字节对齐（zipalign 兼容）。"""
import zipfile, struct, os, zlib

BASE = os.path.dirname(os.path.abspath(__file__))
B = os.path.join(BASE, 'build')
SRC = os.path.join(B, 'app-unsigned.apk')
DEX = os.path.join(B, 'classes.dex')
DST = os.path.join(B, 'aligned.apk')
ALIGN = 4

# 1) 把 dex 合入源包
stage = os.path.join(B, 'stage.apk')
with zipfile.ZipFile(SRC) as zin, zipfile.ZipFile(stage, 'w') as zout:
    for it in zin.infolist():
        zout.writestr(it, zin.read(it.filename))
    with open(DEX, 'rb') as f:
        dex = f.read()
    zi = zipfile.ZipInfo('classes.dex', (1980, 1, 1, 0, 0, 0))
    zout.writestr(zi, dex)

# 2) 重新对齐写出
with zipfile.ZipFile(stage) as zin, open(DST, 'wb') as out:
    entries = []
    for it in zin.infolist():
        data = zin.read(it.filename)
        name = it.filename.encode('utf-8')
        crc = zlib.crc32(data) & 0xffffffff
        comp = zlib.compressobj(6, zlib.DEFLATED, -15)
        cdata = comp.compress(data) + comp.flush()
        method = 8
        if len(cdata) >= len(data):
            cdata = data
            method = 0
        offset = out.tell()
        header_len = 30 + len(name)
        extra = b''
        if method == 0:
            pad = (ALIGN - ((offset + header_len) % ALIGN)) % ALIGN
            if pad:
                extra = struct.pack('<HH', 0xd935, pad) + b'\x00' * pad
        lh = struct.pack('<IHHHHHIIIHH', 0x04034b50, 20, 0, method, 0, 0,
                         crc, len(cdata), len(data), len(name), len(extra))
        out.write(lh); out.write(name); out.write(extra); out.write(cdata)
        entries.append((name, method, crc, len(cdata), len(data), offset, extra))
    cd_off = out.tell()
    for (name, method, crc, csize, usize, offset, extra) in entries:
        ch = struct.pack('<IHHHHHHIIIHHHHHII', 0x02014b50, 20, 20, 0, method, 0, 0,
                         crc, csize, usize, len(name), len(extra), 0, 0, 0, 0, offset)
        out.write(ch); out.write(name); out.write(extra)
    cd_size = out.tell() - cd_off
    out.write(struct.pack('<IHHHHIIH', 0x06054b50, 0, 0, len(entries), len(entries),
                          cd_size, cd_off, 0))

print('  aligned.apk:', os.path.getsize(DST), 'bytes,', len(entries), 'entries')