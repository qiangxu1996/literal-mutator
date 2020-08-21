import json
import sys

if __name__ == '__main__':
    with open(sys.argv[1], encoding='utf-8') as f:
        bookmarks = json.load(f)
    paths = []
    for b in bookmarks:
        for bb in b['bookmarks']:
            path = bb['label'].rsplit('@', 1)[-1]
            paths.append(path + '\n')
    with open(sys.argv[2], 'w', encoding='utf-8') as f:
        f.writelines(paths)
