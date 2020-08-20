package edu.purdue.dsnl.configprof.adaptor;

import edu.purdue.dsnl.configprof.TestState;
import edu.purdue.dsnl.configprof.result.ResultMap;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Log4j2
public class AndroidAdaptor implements AppAdaptor {
	private static final Path OUTPUT_APK = Path.of("app-debug.apk");

	private static final Path SCRIPT_PATH = Path.of("/Users/mh/files/labs/DSNL/projects/PED/impl/automation");

	private static final Path FTRACE_PATH = Path.of("ftrace");

	private static final Path LOGCAT_PATH = Path.of("logcat");

	private final List<String> generalArg = new ArrayList<>();

	private final Conf conf;

	@Data
	public static class Conf {
		private String uiScript;
		private String deviceSerial = null;
		private int appiumPort = -1;
		private double stableThreshold = 0;
	}

	public AndroidAdaptor() throws IOException {
		conf = TestState.getSection("android", Conf.class);
		if (conf == null) {
			throw new IOException("No Android configuration");
		}
		if (conf.deviceSerial != null) {
			generalArg.add("--udid");
			generalArg.add(conf.deviceSerial);
		}
		if (conf.appiumPort > 0) {
			generalArg.add("--server-port");
			generalArg.add(String.valueOf(conf.appiumPort));
		}
		generalArg.add(Path.of(conf.uiScript).toAbsolutePath().toString());

		BUILD_DIR.toFile().mkdir();
		FTRACE_PATH.toFile().mkdir();
		LOGCAT_PATH.toFile().mkdir();
	}

	@SneakyThrows(InterruptedException.class)
	@Override
	public void build(Path project, String tag) throws BuildException, IOException {
		var builder = new ProcessBuilder(project.resolve("build.sh").toAbsolutePath().toString());
		var process = builder.directory(project.toFile()).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
		if (process.waitFor() != 0) {
			throw new BuildException("");
		}

		Files.copy(project.resolve(OUTPUT_APK), getPath(tag), REPLACE_EXISTING);
	}

	@Override
	public void prepare(String tag) throws ExecutionException {
		var command = new ArrayList<String>();
		command.add("adb");
		if (conf.deviceSerial != null) {
			command.add("-s");
			command.add(conf.deviceSerial);
		}
		command.add("install");
		command.add(getPath(tag).toAbsolutePath().toString());
		ExecUtil.execOutput(command);

		command.clear();
		command.add(SCRIPT_PATH.resolve("run_test.sh").toString());
		command.add("--warmup");
		command.addAll(generalArg);
		var output = ExecUtil.execOutput(command);
		if (!output.isBlank()) {
			throw new ExecutionException(output);
		}
	}

	@Override
	public ResultMap run(String tag, String suffix, boolean dummy) throws ExecutionException {
		String id = tag + '_' + suffix;
		if (dummy) {
			id += "_dummy";
		}

		var command = new ArrayList<String>();
		command.add(SCRIPT_PATH.resolve("run_test.sh").toString());
		command.add("--ftrace");
		if (dummy) {
			command.add("--dummy");
		}
		command.add("--ftrace-separate");
		command.add("--ftrace-file");
		command.add(FTRACE_PATH.resolve(id).toAbsolutePath().toString());
		command.add("--logcat");
		command.add(LOGCAT_PATH.resolve(id + ".log").toAbsolutePath().toString());
		command.addAll(generalArg);
		var output = ExecUtil.execOutput(command);
		try {
			var ret = new ResultMap();
			output.lines().map(l -> l.split(" ")).map(l -> Pair.of(l[0], Double.parseDouble(l[1])))
					.forEach(l -> ret.put(l.getKey(), l.getValue()));
			return ret;
		} catch (NumberFormatException e) {
			throw new AppAdaptor.ExecutionException(output);
		}
	}

	@Override
	public void saveState(String tag) throws ExecutionException {
		handleState(tag, false);
	}

	@Override
	public void restoreState(String tag) throws ExecutionException {
		handleState(tag, true);
	}

	private void handleState(String tag, boolean restore) throws ExecutionException {
		var command = new ArrayList<String>();
		command.add(SCRIPT_PATH.resolve("run_test.sh").toString());
		if (restore) {
			command.add("--restore-state");
		} else {
			command.add("--save-state");
		}
		command.add(tag);
		command.addAll(generalArg);
		ExecUtil.exec(command);
	}

	@Override
	public Path getPath(String tag) {
		return BUILD_DIR.resolve(tag + ".apk");
	}

	@Override
	public void delete(String tag) throws IOException {
		Files.delete(getPath(tag));
	}

	@Override
	public boolean isStable(List<ResultMap> results) {
		if (conf.stableThreshold <= 0 || results.isEmpty()) {
			return true;
		}
		var stats = new SummaryStatistics();
		results.stream().mapToDouble(r -> r.values().stream().mapToDouble(Number::doubleValue).sum())
				.forEach(stats::addValue);
		log.debug("results = {}, mean = {}, stdev = {}, threshold = {}",
				results, stats.getMean(), stats.getStandardDeviation(), conf.stableThreshold);
		return stats.getStandardDeviation() <= conf.stableThreshold * stats.getMean();
	}
}
