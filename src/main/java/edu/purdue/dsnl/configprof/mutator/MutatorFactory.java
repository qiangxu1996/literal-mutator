package edu.purdue.dsnl.configprof.mutator;

import edu.purdue.dsnl.configprof.LiteralType;

import java.io.IOException;
import java.util.List;

public class MutatorFactory {
	private MutatorFactory() {}

	public static AbstractMutator createMutator(LiteralType type, List<String> sources) throws IOException {
		return switch (type) {
			case NUM -> new NumericMutator(sources);
			case BOOL -> new BoolMutator(sources);
			case ENUM -> new EnumMutator(sources);
		};
	}
}
