package edu.purdue.dsnl.configprof.adaptor;

import edu.purdue.dsnl.configprof.result.ResultMap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface AppAdaptor {
	Path BUILD_DIR = Path.of("build");

	class BuildException extends Exception {
		public BuildException() {
			super();
		}

		public BuildException(String message) {
			super(message);
		}

		public BuildException(Throwable cause) {
			super(cause);
		}
	}

	class ExecutionException extends Exception {
		public ExecutionException(String message) {
			super(message);
		}
	}

	void build(Path project, String tag) throws BuildException, IOException;

	void prepare(String tag) throws ExecutionException;

	default ResultMap run(String tag, String suffix) throws ExecutionException {
		return run(tag, suffix, false);
	}

	ResultMap run(String tag, String suffix, boolean dummy) throws ExecutionException;

	default void cleanup(String tag) {}

	default void saveState(String tag) throws ExecutionException {}

	default void restoreState(String tag) throws ExecutionException {}

	Path getPath(String tag);

	void delete(String tag) throws IOException;

	default boolean isStable(List<ResultMap> results) {
		return true;
	}
}
