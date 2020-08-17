package edu.purdue.dsnl.configprof.filter;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ISourceFileCoverage;
import org.jacoco.core.tools.ExecFileLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CoverageProcessor {
	private final Map<String, ISourceFileCoverage> sourceFileMap;

	public CoverageProcessor(File execFile, List<File> bytecodePath) throws IOException {
		var loader = new ExecFileLoader();
		loader.load(execFile);

		var coverageBuilder = new CoverageBuilder();
		var analyzer = new Analyzer(loader.getExecutionDataStore(), coverageBuilder);
		for (var p : bytecodePath) {
			analyzer.analyzeAll(p);
		}

		sourceFileMap = coverageBuilder.getSourceFiles().stream()
				.collect(Collectors.toMap(c -> Path.of(c.getPackageName(), c.getName()).toString(), c -> c));
	}

	boolean lineTested(String file, int line, boolean inMethod) {
		var fileCov = sourceFileMap.get(file);
		int status;
		do {
			status = fileCov.getLine(line).getStatus();
			line--;
		} while (inMethod && status == ICounter.EMPTY);
		return status >= ICounter.FULLY_COVERED;
	}
}
