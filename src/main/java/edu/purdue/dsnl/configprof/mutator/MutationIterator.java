package edu.purdue.dsnl.configprof.mutator;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;

public interface MutationIterator extends Iterator<String>, Closeable {
	String nextOrOriginal();

	void resetFile() throws IOException;

	@Override
	default void close() throws IOException {
		resetFile();
	}
}
