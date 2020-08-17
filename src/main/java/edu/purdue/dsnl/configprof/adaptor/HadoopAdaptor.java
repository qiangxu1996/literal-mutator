package edu.purdue.dsnl.configprof.adaptor;

import edu.purdue.dsnl.configprof.result.ResultMap;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class HadoopAdaptor implements AppAdaptor {
	private static final String TERASORT_INPUT = "/user/xu1201/terasort-input";

	private static final String TERASORT_OUTPUT = "/user/xu1201/terasort-output";

	private static final String TERASORT_VALIDATE = "/user/xu1201/terasort-validate";

	private static final String TERASORT_JAR = "share/hadoop/mapreduce/hadoop-mapreduce-examples-3.2.1.jar";

	public HadoopAdaptor() {
		BUILD_DIR.toFile().mkdir();
	}

	@SneakyThrows(InterruptedException.class)
	@Override
	public void build(Path project, String tag) throws BuildException, IOException {
		project = project.toAbsolutePath();
		var cmdBuilder = new ProcessBuilder(project.resolve("start-build-env.sh").toString(),
				"mvn", "package", "-Pdist", "-DskipTests", "-Dmaven.javadoc.skip=true");
		cmdBuilder.directory(project.toFile());
		cmdBuilder.environment().put("DOCKER_INTERACTIVE_RUN", "");
		cmdBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
		var cmd = cmdBuilder.start();
		if (cmd.waitFor() != 0) {
			throw new BuildException(new String(cmd.getErrorStream().readAllBytes()));
		}

		Runtime.getRuntime().exec(new String[]{
				"rsync", "-a", project.resolve("hadoop-dist/target/hadoop-3.2.1").toString() + '/',
				getPath(tag).toString()
		}).waitFor();
	}

	@SneakyThrows(IOException.class)
	@Override
	public void prepare(String tag) throws ExecutionException {
		var build = getPath(tag);
		var hdfs = build.resolve("bin/hdfs").toString();
		var jar = build.resolve(TERASORT_JAR).toAbsolutePath().toString();

		FileUtils.deleteQuietly(new File("/tmp/hadoop-" + System.getProperty("user.name")));
		FileUtils.deleteQuietly(new File("/tmp/hadoop/dfs/journalnode/"));

		exec(hdfs, "namenode", "-format", "-nonInteractive");
		exec(build.resolve("sbin/start-dfs.sh").toString());
		exec(hdfs, "dfsadmin", "-safemode", "wait");
		exec(hdfs, "dfs", "-mkdir", "/user");
		exec(hdfs, "dfs", "-mkdir", "/user/xu1201");
		exec(build.resolve("bin/hadoop").toString(), "jar", jar, "teragen", "10000000", TERASORT_INPUT);
	}

	@SneakyThrows(IOException.class)
	@Override
	public ResultMap run(String tag, String suffix, boolean dummy) throws ExecutionException {
		var build = getPath(tag);
		var hdfs = build.resolve("bin/hdfs").toString();
		var hadoop = build.resolve("bin/hadoop").toString();
		var jar = build.resolve(TERASORT_JAR).toAbsolutePath().toString();

		exec(hdfs, "dfs", "-rm", "-f", "-r", TERASORT_OUTPUT);
		exec(hdfs, "dfs", "-rm", "-f", "-r", TERASORT_VALIDATE);

		long start = System.nanoTime();
		exec(hadoop, "jar", jar, "terasort", TERASORT_INPUT, TERASORT_OUTPUT);
		long stop = System.nanoTime();
		exec(hadoop, "jar", jar, "teravalidate", TERASORT_OUTPUT, TERASORT_VALIDATE);

		var result = new ResultMap();
		result.put("time", (stop - start) / 1000000);
		return result;
	}

	@SneakyThrows({IOException.class, ExecutionException.class})
	@Override
	public void cleanup(String tag) {
		exec(getPath(tag).resolve("sbin/stop-dfs.sh").toString());
	}

	@Override
	public Path getPath(String tag) {
		return BUILD_DIR.resolve(tag);
	}

	@Override
	public void delete(String tag) throws IOException {
		FileUtils.deleteDirectory(getPath(tag).toFile());
	}

	@SneakyThrows(InterruptedException.class)
	private void exec(String... command) throws IOException, ExecutionException {
		var processBuilder = new ProcessBuilder(command);
		processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
		var process = processBuilder.start();
		if (!process.waitFor(2, TimeUnit.MINUTES) || process.exitValue() != 0) {
			throw new ExecutionException(new String(process.getErrorStream().readAllBytes()));
		}
	}
}
