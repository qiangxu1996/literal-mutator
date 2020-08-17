package edu.purdue.dsnl.configprof.result;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Data
public class MutResult {
	private final List<String> paths;

	@Setter(AccessLevel.NONE)
	private List<ResultPerMut> mutations = new ArrayList<>();

	public enum Status {
		FINISH,
		ABORT_COMPILE,
		ABORT_EXEC,
	}

	private static class ResultPerMut {
		List<String> mutation;
		List<ResultMap> refResults;
		List<ResultMap> results;
		Status status;
		String log;
	}

	public void addResults(List<String> mutation, Status status, List<ResultMap> mutationResults) {
		var result = addResults(mutation, status);
		result.results = mutationResults;
	}

	public void addResults(List<String> mutation, Status status,
			List<ResultMap> refResults, List<ResultMap> mutResults) {
		var result = addResults(mutation, status);
		result.refResults = refResults;
		result.results = mutResults;
	}

	public void addResults(List<String> mutation, Status status, String log) {
		var result = addResults(mutation, status);
		result.log = log;
	}

	private ResultPerMut addResults(List<String> mutation, Status status) {
		var result = new ResultPerMut();
		result.mutation = mutation;
		result.status = status;
		mutations.add(result);
		return result;
	}
}
