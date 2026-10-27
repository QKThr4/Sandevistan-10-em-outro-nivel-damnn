"""Texturas dos itens ciberneticos extras e da Clinica de Ripperdoc (pixel art propria)."""
import os
from PIL import Image

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "sandevistan", "textures")
K = (12, 12, 16, 255)
T = (0, 0, 0, 0)
GR = (60, 255, 0, 255)
GR2 = (30, 170, 0, 255)
MET = (120, 126, 136, 255)
MET2 = (82, 88, 98, 255)
MET3 = (170, 176, 186, 255)
CU = (214, 120, 70, 255)
CU2 = (160, 80, 44, 255)
PU = (170, 70, 255, 255)
PU2 = (110, 40, 190, 255)
PU3 = (225, 170, 255, 255)
CY = (80, 230, 255, 255)
RD = (210, 30, 40, 255)


def new(w=16, h=16):
    im = Image.new("RGBA", (w, h), T)
    return im, im.load()


def rect(px, x0, y0, x1, y1, c):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            px[x, y] = c


def outline(im):
    px = im.load()
    w, h = im.size
    pts = []
    for x in range(w):
        for y in range(h):
            if px[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and px[nx, ny][3] > 0 and px[nx, ny] != K:
                        pts.append((x, y))
                        break
    for p in pts:
        px[p] = K
    return im


def cristal():
    im, px = new()
    rows = {2: (7, 8), 3: (6, 9), 4: (5, 10), 5: (5, 10), 6: (4, 11), 7: (4, 11), 8: (4, 11), 9: (5, 10), 10: (5, 10), 11: (6, 9), 12: (7, 8)}
    for y, (a, b) in rows.items():
        for x in range(a, b + 1):
            px[x, y] = PU
    for y in range(3, 12):
        px[6 if y < 9 else 7, y] = PU3
    for y in range(5, 11):
        px[10 if y < 10 else 9, y] = PU2
    px[7, 5] = CY
    px[8, 7] = CY
    return outline(im)


def fibra():
    im, px = new()
    pts = [(2, 12), (3, 11), (4, 10), (5, 9), (6, 8), (7, 7), (8, 6), (9, 5), (10, 4), (11, 3), (12, 2)]
    for i, (x, y) in enumerate(pts):
        px[x, y] = GR if i % 2 == 0 else GR2
        px[x + 1, y] = GR2 if i % 2 == 0 else GR
    pts2 = [(3, 13), (4, 12), (5, 11), (6, 10), (7, 9), (8, 8), (9, 7), (10, 6), (11, 5), (12, 4), (13, 3)]
    for i, (x, y) in enumerate(pts2):
        px[x, y] = GR2 if i % 2 == 0 else GR
    px[12, 1] = CY
    px[2, 13] = CY
    return outline(im)


def ventoinha():
    im, px = new()
    rect(px, 7, 2, 8, 6, MET3)
    rect(px, 7, 9, 8, 13, MET3)
    rect(px, 2, 7, 6, 8, MET3)
    rect(px, 9, 7, 13, 8, MET3)
    rect(px, 3, 3, 5, 4, MET)
    rect(px, 10, 11, 12, 12, MET)
    rect(px, 10, 3, 12, 4, MET)
    rect(px, 3, 11, 5, 12, MET)
    rect(px, 6, 6, 9, 9, MET2)
    rect(px, 7, 7, 8, 8, K)
    return outline(im)


def nucleo():
    im, px = new()
    rect(px, 2, 2, 13, 13, CU)
    rect(px, 2, 2, 13, 3, CU2)
    rect(px, 2, 12, 13, 13, CU2)
    rect(px, 2, 2, 3, 13, CU2)
    rect(px, 12, 2, 13, 13, CU2)
    rect(px, 5, 5, 10, 10, MET2)
    rect(px, 6, 6, 9, 9, CY)
    rect(px, 7, 7, 8, 8, (230, 255, 255, 255))
    for p in ((4, 4), (11, 4), (4, 11), (11, 11)):
        px[p] = MET3
    return outline(im)


def servo():
    im, px = new()
    rect(px, 2, 5, 9, 11, MET)
    rect(px, 2, 5, 9, 6, MET3)
    rect(px, 2, 10, 9, 11, MET2)
    for x in (3, 5, 7):
        rect(px, x, 7, x, 9, CU)
    rect(px, 10, 7, 13, 9, MET3)
    rect(px, 13, 6, 14, 10, MET2)
    px[3, 4] = RD
    return outline(im)


def bateria():
    im, px = new()
    rect(px, 4, 3, 11, 14, PU2)
    rect(px, 4, 3, 5, 14, PU)
    rect(px, 6, 1, 9, 2, CU)
    rect(px, 4, 12, 11, 13, CU2)
    rect(px, 5, 6, 10, 6, CU)
    for y in (7, 8, 9):
        px[7, y] = PU3
        px[8, y] = PU3
    px[7, 10] = CY
    px[8, 10] = CY
    return outline(im)


def clinic_side():
    im, px = new()
    rect(px, 0, 0, 15, 15, MET2)
    rect(px, 0, 0, 15, 0, MET3)
    rect(px, 0, 15, 15, 15, K)
    rect(px, 0, 0, 0, 15, MET)
    rect(px, 15, 0, 15, 15, MET)
    rect(px, 2, 2, 13, 13, MET)
    rect(px, 3, 3, 12, 12, K)
    # cruz vermelha de neon
    rect(px, 7, 4, 8, 11, RD)
    rect(px, 4, 7, 11, 8, RD)
    rect(px, 7, 7, 8, 8, (255, 120, 120, 255))
    # faixa verde neon embaixo
    rect(px, 2, 14, 13, 14, GR)
    return im


def clinic_top():
    im, px = new()
    rect(px, 0, 0, 15, 15, MET)
    rect(px, 0, 0, 15, 0, MET3)
    rect(px, 0, 0, 0, 15, MET3)
    rect(px, 0, 15, 15, 15, MET2)
    rect(px, 15, 0, 15, 15, MET2)
    for i in (4, 8, 12):
        rect(px, i, 1, i, 14, MET2)
        rect(px, 1, i, 14, i, MET2)
    rect(px, 6, 6, 9, 9, K)
    rect(px, 7, 7, 8, 8, GR)
    return im


def clinic_bottom():
    im, px = new()
    rect(px, 0, 0, 15, 15, MET2)
    rect(px, 0, 0, 15, 0, K)
    rect(px, 0, 15, 15, 15, K)
    rect(px, 0, 0, 0, 15, K)
    rect(px, 15, 0, 15, 15, K)
    return im


ITEMS = {"cristal_neural": cristal, "fibra_sinaptica": fibra, "ventoinha": ventoinha, "nucleo": nucleo,
         "servo_motor": servo, "bateria_energia": bateria}
BLOCKS = {"clinica_ripperdoc_side": clinic_side, "clinica_ripperdoc_top": clinic_top,
          "clinica_ripperdoc_bottom": clinic_bottom}

if __name__ == "__main__":
    os.makedirs(os.path.join(BASE, "item"), exist_ok=True)
    os.makedirs(os.path.join(BASE, "block"), exist_ok=True)
    for n, f in ITEMS.items():
        f().save(os.path.join(BASE, "item", n + ".png"))
    for n, f in BLOCKS.items():
        f().save(os.path.join(BASE, "block", n + ".png"))
