import json
import sys


if __name__ == '__main__':
    with open(sys.argv[1], encoding='utf-8') as f:
        mut1 = json.load(f)
    with open(sys.argv[2], encoding='utf-8') as f:
        mut2 = json.load(f)
    with open(sys.argv[3], 'w', encoding='utf-8') as f:
        json.dump(mut1 + mut2, f, indent=4)
