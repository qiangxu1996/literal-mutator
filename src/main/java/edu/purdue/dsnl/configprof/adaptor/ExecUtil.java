package edu.purdue.dsnl.configprof.adaptor;

import lombok.SneakyThrows;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

class ExecUtil {
	private ExecUtil() {}

	static void exec(String... command) throws AppAdaptor.ExecutionException {
		exec(Arrays.asList(command));
	}

	@SneakyThrows({InterruptedException.class, IOException.class})
	static void exec(List<String> command) throws AppAdaptor.ExecutionException {
		var processBuilder = new ProcessBuilder(command);
		processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
		var process = processBuilder.start();
		int ret = process.waitFor();
		if (ret != 0) {
			throw new AppAdaptor.ExecutionException(new String(process.getErrorStream().readAllBytes()));
		}
	}

	@SneakyThrows({InterruptedException.class, IOException.class})
	static String execOutput(List<String> command) {
		var process = new ProcessBuilder(command).redirectErrorStream(true).start();
		process.waitFor();
		return new String(process.getInputStream().readAllBytes());
	}
}
