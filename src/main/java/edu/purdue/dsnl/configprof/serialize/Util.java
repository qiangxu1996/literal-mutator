package edu.purdue.dsnl.configprof.serialize;

import lombok.Cleanup;
import spoon.reflect.cu.SourcePosition;

import java.io.IOException;
import java.nio.file.Files;

public class Util {
	private static final char ANNOTATION_MARK = '@';

	static String getAnnotatedLine(SourcePosition position) throws IOException {
		@Cleanup var lines = Files.lines(position.getFile().toPath());
		var line = lines.skip(position.getLine() - 1).findFirst().get();

		var builder = new StringBuilder(line);
		builder.insert(position.getColumn() - 1, ANNOTATION_MARK);
		builder.insert(position.getEndColumn() + 1, ANNOTATION_MARK);
		return builder.toString().strip();
	}
}
