#!/usr/bin/env python3
"""Para cada receita do mod gera um advancement que da a receita ao pegar qualquer ingrediente."""
import json, os, glob
root = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/data/sandevistan')
out = os.path.join(root, 'advancement', 'recipes')
os.makedirs(out, exist_ok=True)

def items_of(ing):
    if isinstance(ing, list):
        r = []
        for i in ing: r += items_of(i)
        return r
    if 'item' in ing: return [ing['item']]
    if 'tag' in ing: return ['#' + ing['tag']]
    return []

for f in sorted(glob.glob(os.path.join(root, 'recipe', '*.json'))):
    name = os.path.splitext(os.path.basename(f))[0]
    r = json.load(open(f))
    ings = []
    if 'key' in r:
        for v in r['key'].values(): ings += items_of(v)
    if 'ingredients' in r:
        for v in r['ingredients']: ings += items_of(v)
    ings = list(dict.fromkeys(ings))
    criteria = {}
    for i, it in enumerate(ings):
        criteria['has_%d' % i] = {'trigger': 'minecraft:inventory_changed',
                                  'conditions': {'items': [{'items': it}]}}
    criteria['has_the_recipe'] = {'trigger': 'minecraft:recipe_unlocked',
                                  'conditions': {'recipe': 'sandevistan:' + name}}
    adv = {
        'parent': 'minecraft:recipes/root',
        'criteria': criteria,
        'requirements': [list(criteria.keys())],
        'rewards': {'recipes': ['sandevistan:' + name]},
    }
    json.dump(adv, open(os.path.join(out, name + '.json'), 'w'), indent=2)
    print(name, ings)
