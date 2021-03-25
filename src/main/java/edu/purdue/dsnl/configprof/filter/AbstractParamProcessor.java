package edu.purdue.dsnl.configprof.filter;

import edu.purdue.dsnl.configprof.TestState;
import edu.purdue.dsnl.configprof.serialize.LiteralSerializer;
import lombok.SneakyThrows;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.visitor.filter.VariableAccessFilter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AbstractParamProcessor<E extends CtTypedElement<?>> extends AbstractProcessor<E> {
	private final List<Path> sources;

	private final List<CoverageProcessor> coverageProcessors = new ArrayList<>();

	private final List<LiteralSerializer> serializers = new ArrayList<>();

	private final List<String> literalPaths = new ArrayList<>();

	private final FilterLevel filterLevel = FilterLevel.HEURISTIC;

	protected enum FilterLevel {
		NONE,
		CATEGORY,
		HEURISTIC,
		ALL,
	}

	protected AbstractParamProcessor(List<String> sources) {
		this.sources = sources.stream().map(s -> Path.of(s).toAbsolutePath().normalize()).collect(Collectors.toList());
	}

	/**
	 * Child classes should always override this method and call the parent method to avoid bugs in
	 * {@link spoon.support.visitor.ProcessingVisitor#canBeProcessed(CtElement)}
	 */
	@SneakyThrows(IOException.class)
	@Override
	public void process(E element) {
		literalPaths.add(element.getPath().toString());
		for (var s : serializers) {
			s.add(element);
		}
	}

	@SneakyThrows(IOException.class)
	@Override
	public void processingDone() {
		TestState.saveLiteralPaths(literalPaths);
		for (var s : serializers) {
			s.serialize();
		}
	}

	public void addCoverageProcessor(CoverageProcessor processor) {
		coverageProcessors.add(processor);
	}

	public void addSerializer(LiteralSerializer serializer) {
		serializers.add(serializer);
	}

	protected boolean isEnabled(FilterLevel level) {
		return filterLevel.compareTo(level) >= 0;
	}

	/*
	 * Methods handling the variable related to the literal
	 */

	protected boolean isCompatVarType(CtVariable<?> variable) {
		return true;
	}

	protected CtVariable<?> getVariable(CtTypedElement<?> candidate) {
		var varRef = candidate.getParent(element ->
				element instanceof CtField || element instanceof CtLocalVariable || element instanceof CtAssignment);
		CtVariable<?> variable = null;
		if (varRef instanceof CtAssignment) {
			var assigned = ((CtAssignment<?, ?>) varRef).getAssigned();
			if (assigned instanceof CtVariableWrite) {
				variable = ((CtVariableWrite<?>) assigned).getVariable().getDeclaration();
			}
		} else {
			variable = (CtVariable<?>) varRef;
		}

		if (variable != null && isCompatVarType(variable) && noInvokeInBetween(varRef, candidate)) {
			return variable;
		}
		return null;
	}

	protected List<CtVariableAccess<?>> getVariableAccesses(CtVariable<?> candidate) {
		CtElement rootEl = null;
		if (candidate instanceof CtField) {
			if (candidate.isPrivate()) {
				rootEl = candidate.getParent(CtClass.class);
			} else if (candidate.isProtected() || candidate.isPublic()) {
				rootEl = getFactory().Package().getRootPackage();
			} else {
				rootEl = candidate.getParent(CtPackage.class);
			}
		} else if (candidate instanceof CtLocalVariable) {
			rootEl = candidate.getParent(CtMethod.class);
		}

		if (rootEl != null) {
			return rootEl.getElements(new VariableAccessFilter<>(candidate.getReference()));
		}
		return Collections.emptyList();
	}

	protected boolean isWriteAccess(CtVariableAccess<?> access) {
		return access instanceof CtVariableWrite;
	}

	protected boolean isMultiWrite(List<CtVariableAccess<?>> accesses) {
		var writeAccesses = accesses.stream().filter(this::isWriteAccess).collect(Collectors.toList());
		return writeAccesses.size() > 1 || writeAccesses.stream().anyMatch(a -> a.getParent(CtLoop.class) != null);
	}

	/*
	 * Utilities
	 */

	protected boolean noInvokeInBetween(CtElement parent, CtElement child) {
		var invoke = child.getParent(
				element -> element instanceof CtInvocation || element instanceof CtConstructorCall);
		return invoke == null || !invoke.hasParent(parent);
	}

	protected boolean equalOrParent(CtElement parent, CtElement child) {
		return child.equals(parent) || child.hasParent(parent);
	}

	/*
	 * The five categories
	 */

	protected boolean isInit(CtTypedElement<?> candidate) {
		var variable = candidate.getParent(element -> element instanceof CtVariable
				|| element instanceof CtAssignment || element instanceof CtNewArray);
		return variable != null && noContextChangeInBetween(variable, candidate);
	}

	protected boolean isArg(CtTypedElement<?> candidate) {
		var parent = candidate.getParent(
				element -> element instanceof CtInvocation || element instanceof CtConstructorCall);
		if (parent != null && noContextChangeInBetween(parent, candidate)) {
			return !(parent instanceof CtNewClass)
					|| !equalOrParent(((CtNewClass<?>) parent).getAnonymousClass(), candidate);
		}
		return false;
	}

	protected boolean isCond(CtTypedElement<?> candidate) {
		if (hasParentSubstruct(candidate, CtIf.class, CtIf::getCondition)) {
			return true;
		}
		if (hasParentSubstruct(candidate, CtDo.class, CtDo::getLoopingExpression)) {
			return true;
		}
		if (hasParentSubstruct(candidate, CtFor.class, CtFor::getExpression)) {
			return true;
		}
		if (hasParentSubstruct(candidate, CtWhile.class, CtWhile::getLoopingExpression)) {
			return true;
		}
		if (hasParentSubstruct(candidate, CtConditional.class, CtConditional::getCondition)) {
			return true;
		}
		if (candidate.getParent(CtCase.class) != null) {
			return true;
		}
		return false;
	}

	protected boolean isIndex(CtTypedElement<?> candidate) {
		var parent = candidate.getParent(CtArrayAccess.class);
		return parent != null && noContextChangeInBetween(parent, candidate);
	}

	protected boolean isReturn(CtTypedElement<?> candidate) {
		var parent = candidate.getParent(CtReturn.class);
		return parent != null && noContextChangeInBetween(parent, candidate);
	}

	private boolean noContextChangeInBetween(CtElement parent, CtElement child) {
		var context = child.getParent(element -> element instanceof CtArrayAccess
				|| element instanceof CtConstructorCall || element instanceof CtInvocation
				|| element instanceof CtLambda || element instanceof CtNewArray);
		if (context != null && context.hasParent(parent)) {
			return false;
		}

		var ternary = child.getParent(CtConditional.class);
		return ternary == null || !equalOrParent(ternary.getCondition(), child) || !ternary.hasParent(parent);
	}

	private <T extends CtElement> boolean hasParentSubstruct(
			CtElement candidate, Class<T> parentClass, Function<T, CtElement> substructFn) {
		var parent = candidate.getParent(parentClass);
		return parent != null && equalOrParent(substructFn.apply(parent), candidate)
				&& noContextChangeInBetween(parent, candidate);
	}

	/*
	 * Coverage
	 */

	protected boolean isCovered(CtTypedElement<?> candidate) {
		if (coverageProcessors.isEmpty()) {
			return true;
		}

		var inMethod = candidate.getParent(CtBlock.class) != null;

		for (var p : coverageProcessors) {
			var pos = candidate.getPosition();
			var path = packagePath(pos.getFile().getPath());
			if (p.lineTested(path, pos.getLine(), inMethod)) {
				return true;
			}
		}
		return false;
	}

	private String packagePath(String absPath) {
		var path = Path.of(absPath);
		for (var s : sources) {
			if (path.startsWith(s)) {
				return s.relativize(path).toString();
			}
		}
		return null;
	}
}
