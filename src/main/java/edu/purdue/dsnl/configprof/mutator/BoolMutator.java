package edu.purdue.dsnl.configprof.mutator;

import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtElement;

import java.io.IOException;
import java.util.List;

class BoolMutator extends AbstractMutator {
	private class BoolMutationIterator extends AbstractMutationIterator<Boolean> {
		BoolMutationIterator(String path) throws InvalidPathException, IOException {
			super(path);
		}

		BoolMutationIterator(String path, List<String> mut) throws InvalidPathException, IOException {
			super(path, mut);
		}

		@Override
		protected Boolean elementToValue(CtElement el) {
			@SuppressWarnings("unchecked")
			var literal = (CtLiteral<Boolean>) el;
			return literal.getValue();
		}

		@Override
		protected String valueToString(Boolean value) {
			return value.toString();
		}

		@Override
		protected Boolean stringToValue(String str) {
			return Boolean.valueOf(str);
		}

		@Override
		protected List<Boolean> getCandidates() {
			return List.of(!original);
		}
	}

	public BoolMutator(List<String> sources) {
		super(sources);
	}

	@Override
	public MutationIterator getMutations(String path) throws InvalidPathException, IOException {
		return new BoolMutationIterator(path);
	}

	@Override
	public MutationIterator getMutations(String path, List<String> mut) throws InvalidPathException, IOException {
		return new BoolMutationIterator(path, mut);
	}
}
