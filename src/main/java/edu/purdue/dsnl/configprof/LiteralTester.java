package edu.purdue.dsnl.configprof;

import edu.purdue.dsnl.configprof.adaptor.AndroidAdaptor;
import edu.purdue.dsnl.configprof.filter.CoverageProcessor;
import edu.purdue.dsnl.configprof.filter.ProcessorFactory;
import edu.purdue.dsnl.configprof.rpc.BuildWorker;
import edu.purdue.dsnl.configprof.serialize.BookmarkSerializer;
import edu.purdue.dsnl.configprof.serialize.CsvSerializer;
import io.grpc.ServerBuilder;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import spoon.Launcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
@Command
class LiteralTester implements Runnable {
	@CommandLine.Spec
	private CommandLine.Model.CommandSpec spec;

	private static class CovOptions {
		@Option(names = {"-e", "--exec-file"}, required = true)
		List<String> execFiles;

		@Option(names = {"-b", "--bytecode-path"}, required = true)
		List<String> bytecodePaths;
	}

	private List<String> catPaths(String first, List<String> more) {
		return more.stream().map(p -> Path.of(first, p).toString()).collect(Collectors.toList());
	}

	private Launcher createLauncher(List<String> sources) {
		var launcher = new Launcher();
		for (var s : sources) {
			launcher.addInputResource(s);
		}
		return launcher;
	}

	@SneakyThrows(InterruptedException.class)
	private String copyProject(String path) throws IOException {
		Runtime.getRuntime().exec(new String[]{"rsync", "-a", path, "."}).waitFor();
		return Path.of(path).getFileName().toString();
	}

	private Pair<String, List<String>> copyProject(Pair<String, List<String>> original) throws IOException {
		var newPath = copyProject(original.getLeft());
		var sources = catPaths(newPath, original.getRight());
		return Pair.of(newPath, sources);
	}

	@Command
	private void init(@Parameters(index = "0") String project, @Parameters(index = "1..*") List<String> sources,
			@Option(names = {"-t", "--type"}, defaultValue = "NUM") LiteralType type,
			@CommandLine.ArgGroup(exclusive = false) CovOptions cov,
			@Option(names = {"-m", "--bookmark"}) boolean bookmark,
			@Option(names = {"-c", "--csv"}) Path csv) throws IOException {
		TestState.initConf(project, sources);
		TestState.saveTestCounter(0);

		var sourcePaths = catPaths(project, sources);
		var launcher = createLauncher(sourcePaths);
		var processor = ProcessorFactory.createProcessor(type, sourcePaths);
		launcher.addProcessor(processor);

		if (cov != null) {
			var bytecodeFiles = catPaths(project, cov.bytecodePaths)
					.stream().map(File::new).collect(Collectors.toList());
			for (var f : cov.execFiles) {
				processor.addCoverageProcessor(new CoverageProcessor(new File(f), bytecodeFiles));
			}
		}
		if (bookmark) {
			processor.addSerializer(new BookmarkSerializer(Path.of(project)));
		}
		if (csv != null) {
			processor.addSerializer(new CsvSerializer(csv));
		}

		launcher.run();
	}

	@Command
	private void test() throws IOException {
		TestState.loadConf();
		var paths = copyProject(TestState.getProjectPaths());
		var appAdaptor = new AndroidAdaptor();
		var projectBuilder = new ProjectBuilder(paths.getLeft(), paths.getRight(), appAdaptor);
		@Cleanup var driver = new TestDriver(projectBuilder, appAdaptor);
		log.info("Test starts");
		driver.run();
	}

	@Command
	private void worker(@Parameters(index = "0") int port) throws IOException, InterruptedException {
		TestState.loadConf();
		var paths = copyProject(TestState.getProjectPaths());
		var appAdaptor = new AndroidAdaptor();
		log.info("Worker starts");
		ServerBuilder.forPort(port)
				.addService(new BuildWorker(paths.getLeft(), paths.getRight(), appAdaptor))
				.build().start().awaitTermination();
	}

	@Override
	public void run() {
		throw new CommandLine.ParameterException(spec.commandLine(), "Missing required subcommand");
	}

	public static void main(String[] args) {
		new CommandLine(new LiteralTester()).execute(args);
	}
}
