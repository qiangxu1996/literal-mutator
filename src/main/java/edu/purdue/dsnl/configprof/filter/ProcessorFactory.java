package edu.purdue.dsnl.configprof.filter;

import edu.purdue.dsnl.configprof.LiteralType;

import java.util.List;

public class ProcessorFactory {
	private ProcessorFactory() {}

	public static AbstractParamProcessor<?> createProcessor(LiteralType type, List<String> sources) {
		return switch (type) {
			case NUM -> new NumParamProcessor(sources);
			case BOOL -> new BoolParamProcessor(sources);
			case ENUM -> new EnumParamProcessor(sources);
		};
	}
}
