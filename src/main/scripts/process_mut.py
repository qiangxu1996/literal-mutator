import json
import math
import statistics
import sys

import matplotlib.pyplot as plt
from scipy.stats import ttest_ind


STABLE_THRESHOLD = 1
TTEST_THRESHOLD = 0


def sig_left_tail(test, ref):
    ref = [r * (1 - TTEST_THRESHOLD) for r in ref]
    t, p = ttest_ind(test, ref, equal_var=False)
    p /= 2
    if t > 0:
        p = 1 - p
    return p < 0.05


def extract_res(ref_data, mut_data, stable_thresh: float = 1):
    ref_idx = 0
    mileage = ref_data[0]['mileage']
    ref_grp = []
    ref = 0

    param_indices = []
    mut_vals = []
    ref_grps = []
    mut_grps = []

    for i, mut_meta in enumerate(mut_data):
        path = mut_meta['paths'][0]
        while mileage < i:
            ref_idx += 1
            mileage = ref_data[ref_idx]['mileage']
        ref_meta = ref_data[ref_idx]
        ref_grp = [sum(r.values()) for r in ref_meta['results']]
        ref = statistics.mean(ref_grp)
        if statistics.stdev(ref_grp) / ref > stable_thresh:
            print(path)
            continue
        
        lowest = math.inf
        lowest_val = None
        lowest_grp = None
        for mut_val_meta in mut_meta['mutations']:
            if 'results' in mut_val_meta:
                mut_grp = [sum(r.values()) for r in mut_val_meta['results']]
                mut = statistics.mean(mut_grp)
                if statistics.stdev(mut_grp) / mut > stable_thresh:
                    print(path)
                    lowest = math.inf
                    break
                if mut < lowest:
                    lowest, lowest_val, lowest_grp \
                        = mut, mut_val_meta['mutation'][0], mut_grp
        if lowest < math.inf:
            param_indices.append(i)
            mut_vals.append(lowest_val)
            ref_grps.append(ref_grp)
            mut_grps.append(lowest_grp)
        else:
            print('All mutations crash:', path)

    return param_indices, mut_vals, ref_grps, mut_grps


def ref_mut_plot(ref_result_list, mut_result_list):
    x = range(len(ref_result_list))
    ref_results = [statistics.mean(r) for r in ref_result_list]
    mut_results = [statistics.mean(r) for r in mut_result_list]
    ref_errs = [statistics.stdev(r) for r in ref_result_list]
    mut_errs = [statistics.stdev(r) for r in mut_result_list]

    style = {'linestyle': '', 'marker': 'o', 'markersize': 3,
        'elinewidth': 1, 'capsize': 1.5}
    plt.errorbar(x, ref_results, ref_errs, label='Unmodified', **style)
    plt.errorbar(x, mut_results, mut_errs, label='Modified', **style)
    plt.xticks([])
    plt.xlabel('Parameters')
    plt.ylabel('Energy (µAh)')
    plt.legend()


def sig_params(mut_data, param_indices,
    mut_value_list, ref_result_list, mut_result_list):
    sig_indices = []
    for i in range(len(param_indices)):
        if sig_left_tail(mut_result_list[i], ref_result_list[i]):
            sig_indices.append(i)
            idx = param_indices[i]
            print(idx, 1 - statistics.mean(mut_result_list[i]) \
                    / statistics.mean(ref_result_list[i]),
                mut_data[idx]['paths'][0], mut_value_list[i])
    return sig_indices


def sig_plot(param_indices, ref_result_list, mut_result_list, sig_indices):
    fig = plt.gcf()
    fig.set_figheight(fig.get_figheight() / 2)
    fontdict = {'fontsize': 'medium'}

    num_plots = len(sig_indices) if len(sig_indices) < 4 else 4
    last_ax = None
    for i in range(num_plots):
        ax = plt.subplot(1, num_plots, i + 1, sharey=last_ax)
        last_ax = ax
        idx = sig_indices[i]
        grp1 = ref_result_list[idx]
        grp2 = mut_result_list[idx]

        plt.plot(range(len(grp1)), grp1, 'o', label='unmodified')
        plt.plot(range(len(grp1), len(grp1) + len(grp2)), grp2,
            'o', label='modified')
        plt.xticks([])
        plt.title(f'Parameter {param_indices[idx]}', fontdict=fontdict)
        if i == 0:
            plt.ylabel('Energy (μAh)')
        else:
            plt.setp(ax.get_yticklabels(), visible=False)
    h, l = last_ax.get_legend_handles_labels()
    fig.legend(h, l, loc='center right')
    fig.subplots_adjust(right=0.75)


if __name__ == '__main__':
    with open(sys.argv[1], encoding='utf-8') as ref_file, \
            open(sys.argv[2], encoding='utf-8') as mut_file:
        ref_data = json.load(ref_file)
        mut_data = json.load(mut_file)

    param_indices, mut_value_list, ref_result_list, mut_result_list \
        = extract_res(ref_data, mut_data, STABLE_THRESHOLD)

    plt.figure()
    ref_mut_plot(ref_result_list, mut_result_list)
    plt.savefig('ref-mut.pdf')

    print()

    sig_indices = sig_params(mut_data, param_indices,
        mut_value_list, ref_result_list, mut_result_list)
    if len(sig_indices) > 0:
        plt.figure()
        sig_plot(param_indices, ref_result_list, mut_result_list, sig_indices)
        plt.savefig('ref-mut-sig.pdf')
