import json
import sys


if __name__ == '__main__':
    with open(sys.argv[1], encoding='utf-8') as f:
        ref1 = json.load(f)
    with open(sys.argv[2], encoding='utf-8') as f:
        ref2 = json.load(f)
    offset = ref1[-1]['mileage'] + 1
    for r in ref2:
        r['mileage'] += offset
    with open(sys.argv[3], 'w', encoding='utf-8') as f:
        json.dump(ref1 + ref2, f, indent=4)
