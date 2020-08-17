package edu.purdue.dsnl.configprof;

import edu.purdue.dsnl.configprof.adaptor.AppAdaptor;
import edu.purdue.dsnl.configprof.mutator.AbstractMutator;
import edu.purdue.dsnl.configprof.mutator.MutationIterator;
import edu.purdue.dsnl.configprof.mutator.MutatorFactory;
import edu.purdue.dsnl.configprof.rpc.BuildMsg;
import edu.purdue.dsnl.configprof.rpc.WorkerMgr;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

@Log4j2
class ProjectBuilder implements Runnable {
	private static final int BATCH_SIZE = 1;

	private final Path project;

	private final AbstractMutator mutator;

	private final boolean supplyMutation;

	private List<String> paths;

	private List<Pair<String, List<String>>> pathsWithMutations;

	private final AppAdaptor appAdaptor;

	private final int initCounter;

	private final BlockingQueue<BuiltApp> builtApps = new LinkedBlockingQueue<>(2);

	private WorkerMgr workerMgr;

	@Data
	static class BuiltApp {
		private final List<String> paths;
		private final int pathIdx;
		private List<String> mutations;
		private String tag;
		private String log;
		private WorkerMgr.RemotePath remotePath;

		public void setLog(Throwable t) {
			log = t.toString();
		}
	}

	ProjectBuilder(String project, List<String> sources, AppAdaptor appAdaptor) throws IOException {
		this.project = Path.of(project);
		mutator = MutatorFactory.createMutator(TestState.getLiteralType(), sources);
		supplyMutation = TestState.isMutationSupplied();
		if (supplyMutation) {
			pathsWithMutations = TestState.getLiteralPathsWithMutations();
		} else {
			paths = TestState.getLiteralPaths();
		}
		if (TestState.isRpcEnabled()) {
			workerMgr = new WorkerMgr();
		}
		this.appAdaptor = appAdaptor;
		initCounter = TestState.getTestCounter();
	}

	@SneakyThrows(IOException.class)
	@Override
	public void run() {
		var currentThread = Thread.currentThread();
		int numPaths = supplyMutation ? pathsWithMutations.size() : paths.size();
		for (int i = initCounter; i < numPaths && !currentThread.isInterrupted(); i += BATCH_SIZE) {
			try {
				var batchPaths = new ArrayList<String>();
				var mutationIterators = new ArrayList<MutationIterator>();
				try {
					int endBatch = Math.min(i + BATCH_SIZE, numPaths);
					for (int j = i; j < endBatch; j++) {
						String p;
						MutationIterator iterator;
						if (supplyMutation) {
							var pair = pathsWithMutations.get(j);
							p = pair.getLeft();
							iterator = mutator.getMutations(p, pair.getRight());
						} else {
							p = paths.get(j);
							iterator = mutator.getMutations(p);
						}
						batchPaths.add(p);
						mutationIterators.add(iterator);
					}
					while (mutationIterators.stream().anyMatch(MutationIterator::hasNext)) {
						var mutations = new ArrayList<String>();
						for (int j = mutationIterators.size() - 1; j >= 0; j--) {
							mutationIterators.get(j).resetFile();
						}
						for (int j = mutationIterators.size() - 1; j >= 0; j--) {
							mutations.add(0, mutationIterators.get(j).nextOrOriginal());
						}
						var record = new BuiltApp(batchPaths, i);
						record.setMutations(mutations);
						String tag = i + "_" + String.join("_", mutations);
						if (workerMgr != null) {
							buildRpc(batchPaths, mutations, tag, record);
						} else {
							buildLocal(tag, record);
						}
					}
				} catch (AbstractMutator.InvalidPathException e) {
					var record = new BuiltApp(batchPaths, i);
					record.setLog(e);
					putBuiltApp(record);
				} finally {
					for (var m : mutationIterators) {
						m.close();
					}
				}
			} catch (InterruptedException e) {
				log.info("Interrupted", e);
				currentThread.interrupt();
			}
		}

		if (!currentThread.isInterrupted()) {
			try {
				putBuiltApp(new BuiltApp(Collections.emptyList(), numPaths));
			} catch (InterruptedException e) {
				log.error("Interrupted", e);
				currentThread.interrupt();
			}
		}
	}

	private void buildLocal(String tag, BuiltApp record) throws IOException, InterruptedException {
		try {
			appAdaptor.build(project, tag);
			record.setTag(tag);
		} catch (AppAdaptor.BuildException e) {
			record.setLog(e);
		} finally {
			putBuiltApp(record);
		}
	}

	private void buildRpc(List<String> paths, List<String> values, String tag, BuiltApp record) {
		var mutations = BuildMsg.Mutations.newBuilder().setTag(tag);
		for (int i = 0; i < paths.size(); i++) {
			var mut = BuildMsg.Mutations.Mutation.newBuilder().setPath(paths.get(i)).setValue(values.get(i));
			mutations.addMutations(mut);
		}

		workerMgr.build(mutations.build(), new WorkerMgr.AbstractResultHandler() {
			@Override
			public void onNext(BuildMsg.Status value) {
				super.onNext(value);
				record.setTag(tag);
				record.setRemotePath(getRemotePath(value));
			}

			@Override
			public void onError(Throwable t) {
				super.onError(t);
				record.setLog(t);
			}

			@SneakyThrows(InterruptedException.class)
			@Override
			public void onRetire() {
				putBuiltApp(record);
			}
		});
	}

	String buildRef() {
		String tag = "ref";
		try {
			appAdaptor.build(project, tag);
		} catch (AppAdaptor.BuildException | IOException e) {
			log.fatal("Ref build failed", e);
			System.exit(2);
		}
		return tag;
	}

	private void putBuiltApp(BuiltApp app) throws InterruptedException {
		builtApps.put(app);
		log.info("Puts built app {}-{}", app.getPathIdx(), app.getMutations());
	}

	@SneakyThrows(InterruptedException.class)
	BuiltApp nextBuiltApp() throws IOException {
		var next = builtApps.take();
		log.info("Takes built app {}-{}", next.getPathIdx(), next.getMutations());
		if (workerMgr != null && next.tag != null) {
			workerMgr.sync(next.remotePath, appAdaptor.getPath(next.tag));
		}
		return next;
	}
}
