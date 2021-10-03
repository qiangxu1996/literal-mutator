package edu.purdue.dsnl.configprof;

import edu.purdue.dsnl.configprof.adaptor.AppAdaptor;
import edu.purdue.dsnl.configprof.result.MutResult;
import edu.purdue.dsnl.configprof.result.RefResult;
import edu.purdue.dsnl.configprof.result.ResultMap;
import edu.purdue.dsnl.configprof.result.ResultSerializer;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Log4j2
class TestDriver implements Closeable {
	private static final int REPEAT_TEST = 5;

	private static final int RETRY = 2;

	private static final int REF_RUN_INTERVAL = 1;

	private static final boolean RUN_DUMMY = false;

	private static final Path REF_RESULT_FILE = Path.of("ref-results.json");

	private static final Path MUT_RESULT_FILE = Path.of("mut-results.json");

	private final ProjectBuilder builder;

	private final AppAdaptor appAdaptor;

	private final int initCounter = TestState.getTestCounter();

	private final boolean interleave = TestState.isInterleave();

	private String refApk;

	private final ResultSerializer refResultSerializer = new ResultSerializer(REF_RESULT_FILE);

	private final ResultSerializer mutResultSerializer = new ResultSerializer(MUT_RESULT_FILE);

	private interface Executor<T> {
		T exec() throws AppAdaptor.ExecutionException, IOException;
	}

	TestDriver(ProjectBuilder builder, AppAdaptor appAdaptor) throws IOException {
		this.builder = builder;
		this.appAdaptor = appAdaptor;
	}

	void run() throws IOException {
		refApk = builder.buildRef();
		var builderThread = new Thread(builder);
		try {
			builderThread.start();

			int currIdx = -1;
			MutResult record = null;
			while (true) {
				var builtApp = builder.nextBuiltApp();
				var idx = builtApp.getPathIdx();
				var path = builtApp.getPaths();
				boolean idxChanged = false;

				if (idx != currIdx) {
					if (record != null) {
						mutResultSerializer.toJson(record);
						TestState.saveTestCounter(idx);
					}
					record = new MutResult(path);
					currIdx = idx;
					idxChanged = true;
				}

				assert record != null;

				if (path.isEmpty()) {
					break;
				}

				var mutations = builtApp.getMutations();

				if (builtApp.getLog() != null) {
					record.addResults(mutations, MutResult.Status.ABORT_COMPILE, builtApp.getLog());
					continue;
				}

				if (idxChanged && !interleave && (idx - initCounter) % REF_RUN_INTERVAL == 0) {
					try {
						var results = runPairUntilStable(() -> tryAgainIfFail(() -> runRefTest(idx)));
						refResultSerializer.toJson(new RefResult(idx, results.getLeft(), results.getRight()));
					} catch (AppAdaptor.ExecutionException e) {
						log.fatal("Ref run failed", e);
						System.exit(1);
					}
				}

				try {
					if (interleave) {
						int finalCurrIdx = currIdx;
						var results = runPairUntilStable(() ->
								tryAgainIfFail(() -> runInterleaveTest(finalCurrIdx, builtApp.getTag())));
						record.addResults(mutations, MutResult.Status.FINISH, results.getLeft(), results.getRight());
					} else {
						var results = runUntilStable(() -> tryAgainIfFail(() -> runMutTest(builtApp.getTag())));
						record.addResults(mutations, MutResult.Status.FINISH, results);
					}
				} catch (AppAdaptor.ExecutionException e) {
					record.addResults(mutations, MutResult.Status.ABORT_EXEC, e.getMessage());
				}
				appAdaptor.delete(builtApp.getTag());
			}

			if (!interleave) {
				refResultSerializer.finish();
			}
			mutResultSerializer.finish();
		} finally {
			builderThread.interrupt();
		}
	}

	private <T> T tryAgainIfFail(Executor<T> executor) throws IOException, AppAdaptor.ExecutionException {
		for (int i = 0; i < RETRY - 1; i++) {
			try {
				return executor.exec();
			} catch (AppAdaptor.ExecutionException e) {
				log.info("Try again", e);
			}
		}
		return executor.exec();
	}

	private List<ResultMap> runUntilStable(Executor<List<ResultMap>> executor)
			throws IOException, AppAdaptor.ExecutionException {
		log.traceEntry();
		List<ResultMap> results;
		do {
			results = executor.exec();
			log.debug(results);
		} while (!appAdaptor.isStable(results));
		return log.traceExit(results);
	}

	private Pair<List<ResultMap>, List<ResultMap>> runPairUntilStable(
			Executor<Pair<List<ResultMap>, List<ResultMap>>> executor)
			throws IOException, AppAdaptor.ExecutionException {
		log.traceEntry();
		Pair<List<ResultMap>, List<ResultMap>> results;
		do {
			results = executor.exec();
			log.debug(results);
		} while (!appAdaptor.isStable(results.getLeft()) || !appAdaptor.isStable(results.getRight()));
		return log.traceExit(results);
	}

	private Pair<List<ResultMap>, List<ResultMap>> runRefTest(int mileage) throws AppAdaptor.ExecutionException {
		var dummyResults = new ArrayList<ResultMap>();
		var results = new ArrayList<ResultMap>();
		try {
			if (RUN_DUMMY) {
				for (int i = 0; i < REPEAT_TEST; i++) {
					dummyResults.add(appAdaptor.run(refApk, refSuffix(mileage, i), true));
				}
			}
			appAdaptor.prepare(refApk);
			for (int i = 0; i < REPEAT_TEST; i++) {
				results.add(appAdaptor.run(refApk, refSuffix(mileage, i)));
			}
		} finally {
			appAdaptor.cleanup(refApk);
		}
		return Pair.of(dummyResults, results);
	}

	private List<ResultMap> runMutTest(String tag) throws AppAdaptor.ExecutionException {
		var results = new ArrayList<ResultMap>();

		try {
			appAdaptor.prepare(tag);
			for (int i = 0; i < REPEAT_TEST; i++) {
				results.add(appAdaptor.run(tag, String.valueOf(i)));
			}
		} finally {
			appAdaptor.cleanup(tag);
		}

		return results;
	}

	private Pair<List<ResultMap>, List<ResultMap>> runInterleaveTest(int mileage, String tag)
			throws AppAdaptor.ExecutionException {
		var refResults = new ArrayList<ResultMap>();
		var mutResults = new ArrayList<ResultMap>();
		for (int i = 0; i < REPEAT_TEST; i++) {
			try {
				if (i == 0) {
					appAdaptor.prepare(refApk);
				} else {
					appAdaptor.restoreState(refApk);
				}
				refResults.add(appAdaptor.run(refApk, refSuffix(mileage, i)));
			} finally {
				appAdaptor.cleanup(refApk);
				if (i != REPEAT_TEST - 1) {
					appAdaptor.saveState(refApk);
				}
			}
			try {
				if (i == 0) {
					appAdaptor.prepare(tag);
				} else {
					appAdaptor.restoreState(tag);
				}
				mutResults.add(appAdaptor.run(tag, String.valueOf(i)));
			} finally {
				appAdaptor.cleanup(tag);
				if (i != REPEAT_TEST - 1) {
					appAdaptor.saveState(tag);
				}
			}
		}
		return Pair.of(refResults, mutResults);
	}

	String refSuffix(int mileage, int trial) {
		return String.format("%d_%d", mileage, trial);
	}

	@Override
	public void close() throws IOException {
		refResultSerializer.close();
		mutResultSerializer.close();
	}
}
