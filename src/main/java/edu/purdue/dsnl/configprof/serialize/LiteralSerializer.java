package edu.purdue.dsnl.configprof.serialize;

import spoon.reflect.declaration.CtTypedElement;

import java.io.IOException;

public interface LiteralSerializer {
	void add(CtTypedElement<?> element) throws IOException;

	void serialize() throws IOException;
}
