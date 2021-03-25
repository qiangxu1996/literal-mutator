package edu.purdue.dsnl.configprof.serialize;

import com.opencsv.CSVWriter;
import spoon.reflect.declaration.CtTypedElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CsvSerializer implements LiteralSerializer {
	private final CSVWriter writer;

	public CsvSerializer(Path outPath) throws IOException {
		writer = new CSVWriter(Files.newBufferedWriter(outPath));
	}

	@Override
	public void add(CtTypedElement<?> element) throws IOException {
		var fields = new String[2];
		fields[0] = element.getPath().toString();
		fields[1] = Util.getAnnotatedLine(element.getPosition());
		writer.writeNext(fields);
	}

	@Override
	public void serialize() throws IOException {
		writer.close();
	}
}
