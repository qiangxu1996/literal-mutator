import argparse
import csv
import json

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('bookmark')
    parser.add_argument('--csv')
    parser.add_argument('--txt')
    args = parser.parse_args()

    with open(args.bookmark, encoding='utf-8') as f:
        bookmarks = json.load(f)
    if args.csv:
        csvfile = open(args.csv, 'w', newline='')
        csvwriter = csv.writer(csvfile)
    if args.txt:
        pathfile = open(args.txt, 'w', encoding='utf-8')

    for b in bookmarks:
        for bb in b['bookmarks']:
            line, path = bb['label'].rsplit('@', 1)
            if args.csv:
                csvwriter.writerow((path, line))
            if args.txt:
                pathfile.write(path + '\n')
