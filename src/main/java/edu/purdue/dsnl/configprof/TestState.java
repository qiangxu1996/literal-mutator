package edu.purdue.dsnl.configprof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import lombok.Cleanup;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class TestState {
	private static final Path LITERAL_PATHS_FILE = Path.of("literal-paths.txt");

	private static final Path LITERAL_PATHS_WITH_MUTATIONS_FILE = Path.of("literal-mutations.csv");

	private static final File TEST_CONF_FILE = new File("test-conf.yml");

	private static final File TEST_COUNTER_FILE = new File("test-counter.txt");

	private static final String GEN_SEC = "general";

	private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

	private static JsonNode root;

	private static TestConf conf;

	@Data
	private static class TestConf {
		String project;
		List<String> sources;
		LiteralType literalType;
		boolean supplyMutation = false;
		int repeatTest = 5;
		int discardTest = 0;
		boolean interleave = false;
		boolean enableRpc = false;
	}

	private TestState() {}

	public static void saveLiteralPaths(List<String> paths) throws IOException {
		Files.write(LITERAL_PATHS_FILE, paths);
	}

	public static List<String> getLiteralPaths() throws IOException {
		return Files.readAllLines(LITERAL_PATHS_FILE);
	}

	public static void loadConf() throws IOException {
		root = MAPPER.readTree(TEST_CONF_FILE);
		conf = getSection(GEN_SEC, TestConf.class);
	}

	public static void initConf(String project, List<String> sources, LiteralType type) throws IOException {
		conf = new TestConf();
		conf.project = project;
		conf.sources = sources;
		conf.literalType = type;
		var objRoot = MAPPER.createObjectNode();
		objRoot.set(GEN_SEC, MAPPER.valueToTree(conf));
		MAPPER.writeValue(TEST_CONF_FILE, objRoot);
	}

	public static Pair<String, List<String>> getProjectPaths() {
		return Pair.of(conf.project, conf.sources);
	}

	static boolean isMutationSupplied() {
		return conf.supplyMutation;
	}

	static List<Pair<String, List<String>>> getLiteralPathsWithMutations() throws IOException {
		if (!conf.supplyMutation) {
			return Collections.emptyList();
		}
		@Cleanup var reader = new CSVReader(Files.newBufferedReader(LITERAL_PATHS_WITH_MUTATIONS_FILE));
		try {
			return reader.readAll().stream().map(p -> Pair.of(p[0], Arrays.asList(p[1].split(" "))))
					.collect(Collectors.toList());
		} catch (CsvException e) {
			throw new IOException(e);
		}
	}

	public static LiteralType getLiteralType() {
		return conf.literalType;
	}

	public static int getRepeatTest() {
		return conf.repeatTest;
	}

	public static int getDiscardTest() {
		return conf.discardTest;
	}

	public static boolean isInterleave() {
		return conf.interleave;
	}

	public static boolean isRpcEnabled() {
		return conf.enableRpc;
	}

	public static void saveTestCounter(int c) throws IOException {
		@Cleanup var writer = new PrintWriter(TEST_COUNTER_FILE);
		writer.print(c);
	}

	public static int getTestCounter() throws IOException {
		@Cleanup var scanner = new Scanner(TEST_COUNTER_FILE);
		return scanner.nextInt();
	}

	public static <T> T getSection(String section, Class<T> type) throws IOException {
		var n = root.get(section);
		if (n != null) {
			return MAPPER.treeToValue(n, type);
		}
		return null;
	}

	public static <T> T getSection(String section, TypeReference<T> type) {
		var n = root.get(section);
		if (n != null) {
			return MAPPER.convertValue(n, type);
		}
		return null;
	}
}
