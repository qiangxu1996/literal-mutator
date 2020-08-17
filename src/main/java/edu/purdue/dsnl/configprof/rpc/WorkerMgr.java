package edu.purdue.dsnl.configprof.rpc;

import com.fasterxml.jackson.core.type.TypeReference;
import edu.purdue.dsnl.configprof.TestState;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.AllArgsConstructor;
import lombok.Cleanup;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import net.schmizz.sshj.SSHClient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Log4j2
public class WorkerMgr {
	private final BlockingQueue<Worker> freeWorkers = new LinkedBlockingQueue<>();

	private final BlockingQueue<AbstractResultHandler> retirement;

	@Data
	private static class Worker {
		private String host;
		private int port;
		private String user;
		private BuilderGrpc.BuilderStub stub;
	}

	@AllArgsConstructor
	public static class RemotePath {
		private final Worker worker;
		private final String path;
	}

	public abstract static class AbstractResultHandler implements StreamObserver<BuildMsg.Status> {
		private BlockingQueue<Worker> freeWorkers;

		private Queue<AbstractResultHandler> retirement;

		private Worker worker;

		private boolean completed = false;

		@Override
		public void onNext(BuildMsg.Status value) {
			log.info("Remote build from {} succeeded, status {}", worker, value);
		}

		@Override
		public void onCompleted() {
			onCompletedPrivate();
		}

		@Override
		public void onError(Throwable t) {
			log.info("Remote build from {} failed", worker, t);
			onCompletedPrivate();
		}

		public abstract void onRetire();

		public RemotePath getRemotePath(BuildMsg.Status status) {
			return new RemotePath(worker, status.getBuildPath());
		}

		@SneakyThrows(InterruptedException.class)
		private synchronized void onCompletedPrivate() {
			freeWorkers.put(worker);
			completed = true;
			if (retirement.peek() == this) {
				while (retirement.peek() != null && retirement.peek().completed) {
					retirement.remove().onRetire();
				}
			}
		}
	}

	@SneakyThrows(InterruptedException.class)
	public WorkerMgr() throws IOException {
		var workers = TestState.getSection("rpc", new TypeReference<List<Worker>>() {});
		if (workers == null) {
			throw new IOException("No RPC configuration");
		}
		for (var h : workers) {
			var channel = ManagedChannelBuilder.forAddress(h.host, h.port).usePlaintext().build();
			h.stub = BuilderGrpc.newStub(channel);
			freeWorkers.put(h);
		}
		retirement = new LinkedBlockingQueue<>(2 * workers.size());
	}

	@SneakyThrows(InterruptedException.class)
	public void build(BuildMsg.Mutations mutations, AbstractResultHandler handler) {
		var worker = freeWorkers.take();
		handler.freeWorkers = freeWorkers;
		handler.retirement = retirement;
		handler.worker = worker;
		retirement.put(handler);
		log.info("Asking {} to build {}", worker, mutations);
		worker.stub.build(mutations, handler);
	}

	public void sync(RemotePath remotePath, Path localPath) throws IOException {
		log.info("Scp {} to {}", remotePath, localPath);
		@Cleanup var ssh = new SSHClient();
		ssh.loadKnownHosts();
		ssh.connect(remotePath.worker.host);
		ssh.authPublickey(remotePath.worker.user);
		ssh.newSCPFileTransfer().download(remotePath.path, localPath.toAbsolutePath().toString());
		@Cleanup var session = ssh.startSession();
		@Cleanup var cmd = session.exec("rm -r " + remotePath.path);
		cmd.join();
	}
}
