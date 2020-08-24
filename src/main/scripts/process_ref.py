import json
import statistics
import sys

import matplotlib.pyplot as plt
import numpy as np
from scipy.stats import ttest_ind


def extract_res(f):
    with open(f, encoding='utf-8') as ref_file:
        results_list = json.load(ref_file)
    result_grps = []
    results = []
    errs = []
    for result_meta in results_list:
        result_grp = [sum(r.values()) for r in result_meta['results']]
        res = statistics.mean(result_grp)
        err = statistics.stdev(result_grp)
        result_grps.append(result_grp)
        results.append(res)
        errs.append(err)
    return result_grps, results, errs


def energy_time(results, errs):
    plt.errorbar(range(len(results)), results, errs, fmt='.',
        elinewidth=plt.rcParams['lines.linewidth']/2)
    plt.xticks([])
    plt.ylabel('Energy (μAh)')
    plt.xlabel('Time')


def sd_cdf(results, errs):
    percent = [e / r for r, e in zip(results, errs)]
    print(statistics.mean(percent), statistics.stdev(percent))
    percent.sort()
    n = len(percent)
    cdf = [i / n for i in range(1, n + 1)]

    plt.plot(percent, cdf)
    plt.ylabel('CDF')
    plt.xlabel('Normalized Stdev')
    plt.grid(True)


def adjacent_sig(result_grps):
    indices = []
    for i in range(1, len(result_grps)):
        _, p = ttest_ind(result_grps[i - 1], result_grps[i])
        if p < 0.05:
            indices.append(i)
    return indices


def adjacent_plot(result_grps, sig_indices):
    fig = plt.gcf()
    fig.set_figheight(fig.get_figheight() / 2)
    fontdict = {'fontsize': 'medium'}

    n = len(sig_indices)
    last_ax = None
    for i in range(n):
        ax = plt.subplot(1, n, i + 1, sharey=last_ax)
        last_ax = ax
        idx = sig_indices[i]
        grp1 = result_grps[idx-1]
        grp2 = result_grps[idx]

        plt.plot(range(len(grp1)), grp1, 'o')
        plt.plot(range(len(grp1), len(grp1) + len(grp2)), grp2, 'o')
        plt.xticks([])
        plt.title(f'Group {idx-1} and {idx}', fontdict=fontdict)
        if i == 0:
            plt.ylabel('Energy (μAh)')
        else:
            plt.setp(ax.get_yticklabels(), visible=False)


if __name__ == '__main__':
    result_grps, results, errs = extract_res(sys.argv[1])

    print(min(results), max(results))

    plt.figure()
    energy_time(results, errs)
    plt.savefig('ref-energy-time.pdf')

    plt.figure()
    sd_cdf(results, errs)
    plt.savefig('ref-sd-cdf.pdf')

    sig_indices = adjacent_sig(result_grps)
    abs_diff = [2 * (results[i] - results[i-1]) / (results[i] + results[i-1])
        for i in sig_indices]
    for i, d in zip(sig_indices, abs_diff):
        print(i, d)

    plt.figure()
    adjacent_plot(result_grps, sig_indices)
    plt.savefig('ref-adjacent-sig.pdf')
