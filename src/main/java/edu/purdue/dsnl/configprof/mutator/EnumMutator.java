package edu.purdue.dsnl.configprof.mutator;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import edu.purdue.dsnl.configprof.TestState;
import edu.purdue.dsnl.configprof.filter.EnumParamProcessor;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtField;
import spoon.reflect.reference.CtFieldReference;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
class EnumMutator extends AbstractMutator {
	private static final int NUM_ALT_VAL = 3;

	private final Map<String, List<String>> frameworkEnums;

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
			var type = typeRef.getTypeDeclaration();
			if (type != null) {
				Collection<? extends CtFieldReference<?>> fields;
				if (type.isEnum()) {
					fields = ((CtEnum<?>) type).getEnumValues().stream().map(CtField::getReference).toList();
				} else {
					fields = type.getDeclaredFields().stream()
							.filter(f -> EnumParamProcessor.CAPITALIZED.matcher(f.getSimpleName()).matches())
							.toList();
					if (fields.isEmpty()) {
						log.warn("Class declared but no field found for {}", original);
					}
				}
				return fields.stream()
						.filter(f -> !f.equals(original)).limit(NUM_ALT_VAL).collect(Collectors.toList());
			} else {
				var fields = frameworkEnums.get(typeRef.getQualifiedName());
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

	@SneakyThrows(CsvException.class)
	public EnumMutator(List<String> sources) throws IOException {
		super(sources);
		if (TestState.getEnumDefinitionFile() != null) {
			@Cleanup var reader = new CSVReader(Files.newBufferedReader(TestState.getEnumDefinitionFile()));
			frameworkEnums = reader.readAll().stream().collect(
					Collectors.toMap(p -> p[0], p -> Arrays.asList(p[1].split(" "))));
		} else {
			frameworkEnums = Map.of();
		}
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
