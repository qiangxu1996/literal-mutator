package edu.purdue.dsnl.configprof.mutator;

import lombok.SneakyThrows;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtElement;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

class NumericMutator extends AbstractMutator {
	private class NumericMutationIterator extends AbstractMutationIterator<Number> {
		NumberFormat nf = NumberFormat.getInstance();

		NumericMutationIterator(String path) throws InvalidPathException, IOException {
			super(path);
		}

		NumericMutationIterator(String path, List<String> mut) throws InvalidPathException, IOException {
			super(path, mut);
		}

		@Override
		protected Number elementToValue(CtElement el) {
			@SuppressWarnings("unchecked")
			var literal = (CtLiteral<Number>) el;
			return literal.getValue();
		}

		@Override
		protected String valueToString(Number value) {
			String ret;
			if (original instanceof Float || original instanceof Double) {
				ret = String.format("%g", value);
			} else {
				ret = value.toString();
			}
			if (original instanceof Long) {
				ret += 'L';
			} else if (original instanceof Float) {
				ret += 'f';
			}
			return ret;
		}

		@SneakyThrows(ParseException.class)
		@Override
		protected Number stringToValue(String str) {
			return nf.parse(str);
		}

		@Override
		protected List<Number> getCandidates() {
			var candidateList = new ArrayList<Number>();
			if (original instanceof Integer || original instanceof Long || original instanceof Short) {
				long val = original.longValue();
				switch ((int) val) {
					case 0 -> {
						candidateList.add(0xffffff);
						candidateList.add(255);
						candidateList.add(8);
					}
					case 1 -> {
						candidateList.add(8);
						candidateList.add(0);
					}
					default -> {
						candidateList.add(val * 8);
						candidateList.add(Math.max(val / 8, 1));
					}
				}
			} else if (original instanceof Double || original instanceof Float) {
				double val = original.doubleValue();
				if (val == 0) {
					candidateList.add(0.5);
					candidateList.add(1.0);
				} else {
					if (val > 0 && val < 1) {
						candidateList.add(1 - (1 - val) / 8);
					} else {
						candidateList.add(val * 8);
					}
					candidateList.add(val / 8);
				}
			}

			return candidateList;
		}
	}

	public NumericMutator(List<String> sources) {
		super(sources);
	}

	@Override
	public MutationIterator getMutations(String path) throws InvalidPathException, IOException {
		return new NumericMutationIterator(path);
	}

	@Override
	public MutationIterator getMutations(String path, List<String> mut) throws InvalidPathException, IOException {
		return new NumericMutationIterator(path, mut);
	}
}
