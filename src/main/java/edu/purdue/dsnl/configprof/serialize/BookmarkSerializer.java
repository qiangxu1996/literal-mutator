package edu.purdue.dsnl.configprof.serialize;

import com.google.gson.GsonBuilder;
import lombok.Cleanup;
import lombok.Data;
import spoon.reflect.declaration.CtTypedElement;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BookmarkSerializer implements LiteralSerializer {
	private final Path projectRoot;

	private final Map<Path, BookmarkFile> bookmarkFiles = new LinkedHashMap<>();

	private class BookmarkFile {
		final String path;

		List<BookmarkLine> bookmarks = new ArrayList<>();

		BookmarkFile(Path path) {
			this.path = "$ROOTPATH$/" + projectRoot.relativize(path).toString();
		}

		void addLine(CtTypedElement<?> element) throws IOException {
			var position = element.getPosition();
			@Cleanup var lines = Files.lines(position.getFile().toPath());
			var line = lines.skip(position.getLine() - 1).findFirst().get();
			var label = line.strip() + "@" + element.getPath().toString();
			bookmarks.add(new BookmarkLine(position.getLine() - 1, position.getColumn() - 1, label));
		}
	}

	@Data
	private static class BookmarkLine {
		final int line;
		final int column;
		final String label;
	}

	public BookmarkSerializer(Path projectRoot) {
		this.projectRoot = projectRoot.toAbsolutePath();
	}

	@Override
	public void add(CtTypedElement<?> element) throws IOException {
		var bookmarkFile = bookmarkFiles.computeIfAbsent(element.getPosition().getFile().toPath(), BookmarkFile::new);
		bookmarkFile.addLine(element);
	}

	@Override
	public void serialize() throws IOException {
		var gson = new GsonBuilder().setPrettyPrinting().create();
		@Cleanup var writer = new FileWriter("bookmarks.json");
		gson.toJson(bookmarkFiles.values(), writer);
	}
}
