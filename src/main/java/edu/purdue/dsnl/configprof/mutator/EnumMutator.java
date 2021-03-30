package edu.purdue.dsnl.configprof.mutator;

import lombok.extern.log4j.Log4j2;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtFieldReference;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Map.entry;

@Log4j2
class EnumMutator extends AbstractMutator {
	private static final int NUM_ALT_VAL = 3;

	private static final Map<String, List<String>> ANDROID_ENUM_FIELDS = Map.ofEntries();

	private class EnumMutationIterator extends AbstractMutationIterator<CtFieldReference<?>> {
		EnumMutationIterator(String path) throws InvalidPathException, IOException {
			super(path);
		}

		EnumMutationIterator(String path, List<String> mut) throws InvalidPathException, IOException {
			super(path, mut);
		}

		@Override
		protected CtFieldReference<?> elementToValue(CtElement el) {
			return ((CtFieldRead<?>) el).getVariable();
		}

		@Override
		protected String valueToString(CtFieldReference<?> value) {
			return value.getDeclaringType().getQualifiedName().replace('$', '.') + '.' + value.getSimpleName();
		}

		@Override
		protected CtFieldReference<?> stringToValue(String str) {
			var names = str.split("\\.");
			return getRef(names[names.length - 1]);
		}

		@Override
		protected List<CtFieldReference<?>> getCandidates() {
			var typeRef = original.getDeclaringType();
			if (typeRef.getDeclaration() != null) {
				return typeRef.getDeclaredFields().stream()
						.filter(f -> !f.equals(original)).limit(NUM_ALT_VAL).collect(Collectors.toList());
			} else {
				var fields = ANDROID_ENUM_FIELDS.get(typeRef.getQualifiedName());
				if (fields != null) {
					return fields.stream().filter(f -> !f.equals(original.getSimpleName()))
							.limit(NUM_ALT_VAL).map(this::getRef).collect(Collectors.toList());
				} else {
					log.warn("Didn't find other fields for {}", original);
					return Collections.emptyList();
				}
			}
		}

		private CtFieldReference<?> getRef(String name) {
			var clone = original.clone();
			clone.setSimpleName(name);
			return clone;
		}
	}

	public EnumMutator(List<String> sources) {
		super(sources);
	}

	@Override
	public MutationIterator getMutations(String path) throws InvalidPathException, IOException {
		return new EnumMutationIterator(path);
	}

	@Override
	public MutationIterator getMutations(String path, List<String> mut) throws InvalidPathException, IOException {
		return new EnumMutationIterator(path, mut);
	}
}
