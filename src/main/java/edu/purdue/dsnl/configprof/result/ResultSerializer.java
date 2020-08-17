package edu.purdue.dsnl.configprof.result;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.*;

public class ResultSerializer implements Closeable {
	private final BufferedWriter writer;

	private final Gson gson;

	private boolean append;

	public ResultSerializer(Path outFile) throws IOException {
		gson = new GsonBuilder().setPrettyPrinting().create();
		writer = Files.newBufferedWriter(outFile, CREATE, APPEND, WRITE);
		append = outFile.toFile().length() != 0;
	}

	public void toJson(Object src) throws IOException {
		if (append) {
			writer.write(',');
			writer.newLine();
		} else {
			writer.write('[');
			writer.newLine();
			append = true;
		}
		gson.toJson(src, writer);
		writer.flush();
	}

	public void finish() throws IOException {
		writer.write(']');
		writer.newLine();
		writer.flush();
		append = false;
	}

	@Override
	public void close() throws IOException {
		writer.close();
	}
}
