#!/usr/bin/env python3
"""Gera data/sandevistan/structure/village/ripperdoc_clinic.nbt (garagem do doutor Ripperdoc) sem libs externas."""
import gzip, struct, os, sys

OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/data/sandevistan/structure/village/ripperdoc_clinic.nbt')

# ---------- escritor NBT minimo ----------
class B(int): pass            # byte
class S(int): pass            # short
class L(int): pass            # long
class F(float): pass          # float
class D(float): pass          # double
class IA(list): pass          # int array

def tid(v):
    if isinstance(v, B): return 1
    if isinstance(v, S): return 2
    if isinstance(v, L): return 4
    if isinstance(v, bool): return 1
    if isinstance(v, int): return 3
    if isinstance(v, F): return 5
    if isinstance(v, D): return 6
    if isinstance(v, float): return 6
    if isinstance(v, str): return 8
    if isinstance(v, IA): return 11
    if isinstance(v, list): return 9
    if isinstance(v, dict): return 10
    raise TypeError(type(v))

def wstr(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b

def payload(v):
    t = tid(v)
    if t == 1: return struct.pack('>b', int(v))
    if t == 2: return struct.pack('>h', int(v))
    if t == 3: return struct.pack('>i', int(v))
    if t == 4: return struct.pack('>q', int(v))
    if t == 5: return struct.pack('>f', float(v))
    if t == 6: return struct.pack('>d', float(v))
    if t == 8: return wstr(v)
    if t == 11: return struct.pack('>i', len(v)) + b''.join(struct.pack('>i', x) for x in v)
    if t == 9:
        if not v: return struct.pack('>bi', 0, 0)
        et = tid(v[0])
        return struct.pack('>bi', et, len(v)) + b''.join(payload(x) for x in v)
    if t == 10:
        out = b''
        for k, x in v.items():
            out += struct.pack('>b', tid(x)) + wstr(k) + payload(x)
        return out + b'\x00'

def write_nbt(path, root):
    data = struct.pack('>b', 10) + wstr('') + payload(root)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.GzipFile(path, 'wb', mtime=0) as f:
        f.write(data)

# ---------- a construcao ----------
W, H, DZ = 11, 9, 10          # x, y, z
grid = {}                     # (x,y,z) -> (nome, props, nbt)

def put(x, y, z, name, props=None, nbt=None):
    grid[(x, y, z)] = (name, props or {}, nbt)

def box(x0, x1, y0, y1, z0, z1, name, props=None):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                put(x, y, z, name, props)

# limpa o volume (tira grama, flores etc.)
box(0, W - 1, 1, H - 1, 0, DZ - 1, 'minecraft:air')
# fundacao
box(0, W - 1, 0, 0, 0, DZ - 1, 'minecraft:stone_bricks')
# calcada da frente (z=0)
box(0, W - 1, 1, 1, 0, 0, 'minecraft:polished_andesite')
# piso interno xadrez
for x in range(1, W - 1):
    for z in range(2, DZ - 1):
        put(x, 1, z, 'minecraft:polished_blackstone' if (x + z) % 2 == 0 else 'minecraft:polished_deepslate')
# soleira com listras de perigo (z=1)
for x in range(0, W):
    put(x, 1, 1, 'minecraft:yellow_concrete' if x % 2 == 0 else 'minecraft:black_concrete')
# fundo/laterais base
for y in range(1, 7):
    for x in range(0, W):
        for z in (1, DZ - 1):
            if y >= 2 or z == DZ - 1:
                put(x, y, z, 'minecraft:gray_concrete' if y < 6 else 'minecraft:black_concrete')
    for z in range(1, DZ):
        for x in (0, W - 1):
            put(x, y, z, 'minecraft:gray_concrete' if y < 6 else 'minecraft:black_concrete')
# base escura nas paredes
for x in range(0, W):
    for z in (1, DZ - 1):
        if z != 1:
            put(x, 1, z, 'minecraft:black_concrete')
for z in range(2, DZ):
    for x in (0, W - 1):
        put(x, 1, z, 'minecraft:black_concrete')
# pilares nos cantos
for x in (0, W - 1):
    for z in (1, DZ - 1):
        for y in range(1, 7):
            put(x, y, z, 'minecraft:polished_blackstone_bricks')
# teto
box(0, W - 1, 6, 6, 1, DZ - 1, 'minecraft:black_concrete')
# parapeito
for x in range(0, W):
    for z in range(1, DZ):
        if x in (0, W - 1) or z in (1, DZ - 1):
            put(x, 7, z, 'minecraft:polished_blackstone_brick_slab', {'type': 'bottom'})
# antena
put(8, 7, 7, 'minecraft:lightning_rod', {'facing': 'up'})
put(8, 8, 7, 'minecraft:end_rod', {'facing': 'up'})
# luzes do teto (embutidas)
for (x, z) in [(3, 4), (7, 4), (3, 7), (7, 7), (5, 3), (5, 7)]:
    put(x, 6, z, 'minecraft:sea_lantern')
# porta de garagem aberta (x=3..7, y=2..4 em z=1)
box(3, 7, 2, 4, 1, 1, 'minecraft:air')
box(3, 7, 5, 5, 1, 1, 'minecraft:black_concrete')
put(2, 2, 1, 'minecraft:polished_blackstone_bricks'); put(8, 2, 1, 'minecraft:polished_blackstone_bricks')
for y in (2, 3, 4):
    put(2, y, 1, 'minecraft:polished_blackstone_bricks'); put(8, y, 1, 'minecraft:polished_blackstone_bricks')
# neon vermelho no topo da porta (interno)
box(3, 7, 5, 5, 2, 2, 'minecraft:red_concrete')
# janelas (vidro vermelho)
for x in (1, 9):
    for y in (3, 4):
        put(x, y, 1, 'minecraft:red_stained_glass_pane', {'east': 'true', 'west': 'true'})
for z in (4, 5, 6):
    for y in (3, 4):
        for x in (0, W - 1):
            put(x, y, z, 'minecraft:red_stained_glass_pane', {'north': 'true', 'south': 'true'})
# janelas altas no fundo
for x in (2, 8):
    for y in (4, 5):
        put(x, y, DZ - 1, 'minecraft:cyan_stained_glass_pane', {'east': 'true', 'west': 'true'})
# placa na fachada
sign_msgs = ['{"text":""}', '{"text":"RIPPERDOC","bold":true}', '{"text":"CLINIC"}', '{"text":""}']
put(5, 5, 0, 'minecraft:crimson_wall_sign', {'facing': 'north'}, {
    'id': 'minecraft:sign',
    'is_waxed': B(1),
    'front_text': {'messages': sign_msgs, 'color': 'red', 'has_glowing_text': B(1)},
    'back_text': {'messages': ['{"text":""}'] * 4, 'color': 'black', 'has_glowing_text': B(0)},
})
# lanternas na entrada
for x in (2, 8):
    put(x, 2, 0, 'minecraft:polished_blackstone_wall', {'up': 'true'})
    put(x, 3, 0, 'minecraft:soul_lantern')
# ---- interior ----
# tapete vermelho da porta ate a mesa
for z in range(2, 5):
    put(5, 2, z, 'minecraft:red_carpet')
# bloco da clinica (posto de trabalho do doutor) no centro
put(5, 2, 5, 'sandevistan:clinica_ripperdoc')
# lampada cirurgica
put(5, 5, 5, 'minecraft:chain', {'axis': 'y'})
put(5, 4, 5, 'minecraft:soul_lantern', {'hanging': 'true'})
for (x, z) in [(2, 3), (8, 3), (2, 7), (8, 7)]:
    put(x, 5, z, 'minecraft:chain', {'axis': 'y'})
    put(x, 4, z, 'minecraft:lantern', {'hanging': 'true'})
# cadeira do paciente
put(3, 2, 5, 'minecraft:quartz_stairs', {'facing': 'east', 'half': 'bottom'})
put(3, 2, 6, 'minecraft:quartz_slab', {'type': 'bottom'})
put(7, 2, 5, 'minecraft:quartz_stairs', {'facing': 'west', 'half': 'bottom'})
# parede esquerda: suprimentos
put(1, 2, 3, 'minecraft:barrel', {'facing': 'up'}); put(1, 2, 4, 'minecraft:barrel', {'facing': 'up'})
put(1, 3, 3, 'minecraft:bookshelf'); put(1, 3, 4, 'minecraft:bookshelf')
put(1, 2, 6, 'minecraft:cauldron')
put(1, 2, 7, 'minecraft:brewing_stand')
# parede direita: bancada
put(9, 2, 3, 'minecraft:smithing_table')
put(9, 2, 4, 'minecraft:grindstone', {'face': 'floor', 'facing': 'west'})
put(9, 2, 6, 'minecraft:anvil', {'facing': 'north'})
put(9, 2, 7, 'minecraft:lectern', {'facing': 'west'})
# fundo: maquinas, telas e estante
for x in (2, 3):
    put(x, 2, 8, 'minecraft:blast_furnace', {'facing': 'north'})
    put(x, 3, 8, 'minecraft:sea_lantern')
for x in (7, 8):
    put(x, 2, 8, 'minecraft:smoker', {'facing': 'north'})
    put(x, 3, 8, 'minecraft:sea_lantern')
for x in (4, 5, 6):
    put(x, 2, 8, 'minecraft:bookshelf'); put(x, 3, 8, 'minecraft:bookshelf')
# a placa de neon verde-agua atras da estante
put(5, 4, 8, 'minecraft:cyan_concrete')

# ---------- monta o arquivo ----------
palette, index = [], {}
def pid(name, props):
    key = (name, tuple(sorted(props.items())))
    if key not in index:
        index[key] = len(palette)
        e = {'Name': name}
        if props: e['Properties'] = dict(props)
        palette.append(e)
    return index[key]

# jigsaw de entrada: virado para o norte (fora da casa), na calcada
put(5, 1, 0, 'minecraft:jigsaw', {'orientation': 'north_up'}, {
    'id': 'minecraft:jigsaw',
    'name': 'minecraft:building_entrance',
    'target': 'minecraft:building_entrance',
    'pool': 'minecraft:empty',
    'final_state': 'minecraft:polished_andesite',
    'joint': 'rollable',
})

blocks = []
for (x, y, z) in sorted(grid):
    name, props, nbt = grid[(x, y, z)]
    b = {'pos': [x, y, z], 'state': pid(name, props)}
    if nbt: b['nbt'] = nbt
    blocks.append(b)

villager = {
    'id': 'minecraft:villager',
    'VillagerData': {'profession': 'sandevistan:ripperdoc', 'level': 2, 'type': 'minecraft:plains'},
    'Xp': 10,
    'PersistenceRequired': B(1),
    'CustomName': '{"translate":"sandevistan.doc.name"}',
    'CustomNameVisible': B(1),
    'Rotation': [F(180.0), F(0.0)],
    'Offers': {'Recipes': []},
}
root = {
    'DataVersion': 3955,
    'size': [W, H, DZ],
    'palette': palette,
    'blocks': blocks,
    'entities': [{'pos': [D(5.5), D(2.0), D(3.5)], 'blockPos': [5, 2, 3], 'nbt': villager}],
}
write_nbt(os.path.abspath(OUT), root)
print('ok', len(blocks), 'blocos,', len(palette), 'estados ->', os.path.abspath(OUT))
