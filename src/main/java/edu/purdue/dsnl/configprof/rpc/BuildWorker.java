package edu.purdue.dsnl.configprof.rpc;

import edu.purdue.dsnl.configprof.TestState;
import edu.purdue.dsnl.configprof.adaptor.AppAdaptor;
import edu.purdue.dsnl.configprof.mutator.AbstractMutator;
import edu.purdue.dsnl.configprof.mutator.MutationIterator;
import edu.purdue.dsnl.configprof.mutator.MutatorFactory;
import io.grpc.stub.StreamObserver;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Log4j2
public class BuildWorker extends BuilderGrpc.BuilderImplBase {
	private final Path project;

	private final AbstractMutator mutator;

	private final AppAdaptor appAdaptor;

	public BuildWorker(String project, List<String> sources, AppAdaptor appAdaptor) throws IOException {
		this.project = Path.of(project);
		mutator = MutatorFactory.createMutator(TestState.getLiteralType(), sources);
		this.appAdaptor = appAdaptor;
	}

	@Override
	public void build(BuildMsg.Mutations request, StreamObserver<BuildMsg.Status> responseObserver) {
		log.info("Accepts build request {}", request);
		var mutations = new ArrayList<MutationIterator>();
		try {
			try {
				for (var m : request.getMutationsList()) {
					var mm = mutator.getMutations(m.getPath(), List.of(m.getValue()));
					mutations.add(mm);
					mm.next();
				}
				String tag = request.getTag();
				appAdaptor.build(project, tag);
				String path = appAdaptor.getPath(tag).toAbsolutePath().toString();
				log.info("Build successful, stored to {}", path);
				responseObserver.onNext(BuildMsg.Status.newBuilder().setBuildPath(path).build());
				responseObserver.onCompleted();
			} finally {
				for (var m : mutations) {
					m.close();
				}
			}
		} catch (IOException | AppAdaptor.BuildException | AbstractMutator.InvalidPathException e) {
			log.info("Build failed", e);
			responseObserver.onError(e);
		}
	}
}
