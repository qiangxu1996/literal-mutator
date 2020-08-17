package edu.purdue.dsnl.configprof.mutator;

import lombok.SneakyThrows;
import spoon.Launcher;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.path.CtPathStringBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public abstract class AbstractMutator {
	private final Launcher launcher = new Launcher();

	private static final Path OUTPUT_DIR = Path.of("bak");

	private static final Path TMP_FILE = Path.of("bak/tmp.java");

	protected abstract class AbstractMutationIterator<T> implements MutationIterator {
		protected final T original;

		private final Iterator<String> candidates;

		private final SourcePosition position;

		private final Path targetFile;

		private final Path bakFile;

		protected AbstractMutationIterator(String path) throws InvalidPathException, IOException {
			this(path, null);
		}

		protected AbstractMutationIterator(String path, List<String> mut) throws InvalidPathException, IOException {
			var element = getElement(path);
			original = elementToValue(element);
			if (mut != null) {
				candidates = mut.stream().map(this::stringToValue).map(this::valueToString).iterator();
			} else {
				candidates = getCandidates().stream().map(this::valueToString).iterator();
			}

			position = element.getPosition();
			targetFile = position.getFile().toPath();
			bakFile = OUTPUT_DIR.resolve(targetFile.getFileName());
			Files.copy(targetFile, bakFile, REPLACE_EXISTING);
		}

		protected abstract T elementToValue(CtElement el);

		protected abstract String valueToString(T value);

		protected abstract T stringToValue(String str);

		protected abstract List<T> getCandidates();

		@Override
		public boolean hasNext() {
			return candidates.hasNext();
		}

		@SneakyThrows(IOException.class)
		@Override
		public String next() {
			var candidate = candidates.next();
			output(candidate);
			return candidate;
		}

		@Override
		public String nextOrOriginal() {
			if (hasNext()) {
				return next();
			} else {
				return valueToString(original);
			}
		}

		@Override
		public void resetFile() throws IOException {
			Files.copy(bakFile, targetFile, REPLACE_EXISTING);
		}

		private CtElement getElement(String path) throws InvalidPathException {
			var root = launcher.getModel().getRootPackage();
			var ctPath = new CtPathStringBuilder().fromString(path);
			var els = ctPath.evaluateOn(root);
			if (els.size() != 1) {
				throw new InvalidPathException();
			}
			return els.get(0);
		}

		private void output(String val) throws IOException {
			try (var in = Files.newBufferedReader(targetFile); var out = Files.newBufferedWriter(TMP_FILE)) {
				long ret;
				int start = position.getSourceStart();
				int end = position.getSourceEnd();

				var buf = new char[start];
				ret = in.read(buf);
				assert ret == start;
				out.write(buf);

				long toSkip = end - start + 1L;
				ret = in.skip(toSkip);
				assert ret == toSkip;
				out.write(val);

				in.transferTo(out);
			}

			Files.copy(TMP_FILE, targetFile, REPLACE_EXISTING);
		}
	}

	public static class InvalidPathException extends Exception {}

	public AbstractMutator(List<String> sources) {
		sources.forEach(launcher::addInputResource);
		launcher.buildModel();
		OUTPUT_DIR.toFile().mkdir();
	}

	public abstract MutationIterator getMutations(String path) throws InvalidPathException, IOException;

	public abstract MutationIterator getMutations(String path, List<String> mut)
			throws InvalidPathException, IOException;
}
