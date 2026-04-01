import struct
import zlib

W, H = 128, 128
BG_TOP = (13, 17, 23)
BG_BOTTOM = (22, 27, 34)
ACCENT = (88, 166, 255)
WHITE = (240, 246, 252)
RED = (248, 81, 73)


def in_rounded_rect(x, y, x1, y1, x2, y2, r):
    if x1 + r <= x <= x2 - r or y1 + r <= y <= y2 - r:
        return True
    corners = ((x1 + r, y1 + r), (x2 - r, y1 + r), (x1 + r, y2 - r), (x2 - r, y2 - r))
    for cx, cy in corners:
        if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
            return True
    return False


pixels = bytearray()
for y in range(H):
    pixels.append(0)
    t = y / (H - 1)
    bg = (
        int(BG_TOP[0] * (1 - t) + BG_BOTTOM[0] * t),
        int(BG_TOP[1] * (1 - t) + BG_BOTTOM[1] * t),
        int(BG_TOP[2] * (1 - t) + BG_BOTTOM[2] * t),
    )
    for x in range(W):
        if not in_rounded_rect(x, y, 6, 6, 121, 121, 20):
            pixels.extend([0, 0, 0, 0])
        else:
            pixels.extend([*bg, 255])


def set_pixel(px, x, y, color):
    if 0 <= x < W and 0 <= y < H:
        off = (y * (1 + W * 4)) + 1 + x * 4
        px[off : off + 4] = bytes([*color, 255])


def fill_rect(px, x1, y1, x2, y2, color):
    for yy in range(y1, y2 + 1):
        for xx in range(x1, x2 + 1):
            set_pixel(px, xx, yy, color)


def draw_border(px, x1, y1, x2, y2, t, color):
    fill_rect(px, x1, y1, x2, y1 + t, color)
    fill_rect(px, x1, y2 - t, x2, y2, color)
    fill_rect(px, x1, y1, x1 + t, y2, color)
    fill_rect(px, x2 - t, y1, x2, y2, color)


# Outer accent border
draw_border(pixels, 10, 10, 117, 117, 2, ACCENT)

# Document base
fill_rect(pixels, 24, 18, 98, 108, WHITE)
draw_border(pixels, 24, 18, 98, 108, 2, ACCENT)

# Folded corner
for yy in range(18, 42):
    for xx in range(74, 98):
        if xx - 74 >= yy - 18:
            set_pixel(pixels, xx, yy, BG_BOTTOM)
draw_border(pixels, 74, 18, 98, 42, 1, ACCENT)

# Document lines
fill_rect(pixels, 34, 50, 78, 54, ACCENT)
fill_rect(pixels, 34, 62, 72, 66, ACCENT)
fill_rect(pixels, 34, 74, 66, 78, ACCENT)

# Alert badge
for y in range(68, 109):
    for x in range(67, 108):
        if (x - 87) ** 2 + (y - 88) ** 2 <= 19 * 19:
            set_pixel(pixels, x, y, RED)
fill_rect(pixels, 85, 77, 89, 91, WHITE)
fill_rect(pixels, 85, 95, 89, 99, WHITE)

def make_png(width, height, data):
    def chunk(ctype, cdata):
        c = ctype + cdata
        return struct.pack('>I', len(cdata)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    sig = b'\x89PNG\r\n\x1a\n'
    ihdr = chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
    compressed = zlib.compress(bytes(data), 9)
    idat = chunk(b'IDAT', compressed)
    iend = chunk(b'IEND', b'')
    return sig + ihdr + idat + iend

png = make_png(W, H, pixels)
with open('icon.png', 'wb') as f:
    f.write(png)
print('icon.png written: {} bytes'.format(len(png)))
