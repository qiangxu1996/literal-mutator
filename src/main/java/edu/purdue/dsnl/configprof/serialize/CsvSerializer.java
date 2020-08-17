package edu.purdue.dsnl.configprof.serialize;

import com.opencsv.CSVWriter;
import lombok.Cleanup;
import spoon.reflect.declaration.CtTypedElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CsvSerializer implements LiteralSerializer {
	private static final char ANNOTATION_MARK = '@';

	private final CSVWriter writer;

	public CsvSerializer(Path outPath) throws IOException {
		writer = new CSVWriter(Files.newBufferedWriter(outPath));
	}

	@Override
	public void add(CtTypedElement<?> element) throws IOException {
		var fields = new String[2];
		var position = element.getPosition();

		fields[0] = element.getPath().toString();

		String line = getLine(position.getFile().toPath(), position.getLine());
		fields[1] = annotate(line, position.getColumn(), position.getEndColumn());

		writer.writeNext(fields);
	}

	@Override
	public void serialize() throws IOException {
		writer.close();
	}

	private String getLine(Path path, int line) throws IOException {
		@Cleanup var lines = Files.lines(path);
		return lines.skip(line - 1).findFirst().get();
	}

	private String annotate(String line, int column, int columnEnd) {
		var builder = new StringBuilder(line);
		builder.insert(column - 1, ANNOTATION_MARK);
		builder.insert(columnEnd + 1, ANNOTATION_MARK);
		return builder.toString();
	}
}
