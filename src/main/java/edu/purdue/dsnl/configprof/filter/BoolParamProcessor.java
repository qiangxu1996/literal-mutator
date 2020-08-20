package edu.purdue.dsnl.configprof.filter;

import spoon.reflect.code.*;
import spoon.reflect.declaration.CtTypedElement;
import spoon.reflect.declaration.CtVariable;

import java.util.ArrayList;
import java.util.List;

class BoolParamProcessor extends AbstractParamProcessor<CtLiteral<?>> {
	BoolParamProcessor(List<String> sources) {
		super(sources);
	}

	@Override
	public boolean isToBeProcessed(CtLiteral<?> candidate) {
		if (!isBool(candidate)) {
			return false;
		}
		if (isEnabled(FilterLevel.CATEGORY) && !(isInit(candidate) || isArg(candidate))) {
			return false;
		}
		if (isEnabled(FilterLevel.HEURISTIC) && isOneArgInvoke(candidate)) {
			return false;
		}
		if (isEnabled(FilterLevel.ALL) && (isReturn(candidate) || isCondAssign(candidate))) {
			return false;
		}

		var toBeChecked = new ArrayList<CtTypedElement<?>>();
		toBeChecked.add(candidate);

		var variable = getVariable(candidate);
		if (variable != null) {
			var accesses = getVariableAccesses(variable);
			if (isEnabled(FilterLevel.HEURISTIC) && isMultiWrite(accesses)) {
				return false;
			}
			accesses.removeIf(this::isWriteAccess);
			toBeChecked.addAll(accesses);
		}

		return toBeChecked.stream().anyMatch(this::isCovered);
	}

	@Override
	public void process(CtLiteral<?> element) {
		super.process(element);
	}

	@Override
	protected boolean isCompatVarType(CtVariable<?> variable) {
		return isBool(variable);
	}

	private boolean isBool(CtTypedElement<?> candidate) {
		var type = candidate.getType().unbox();
		var typeFactory = getFactory().Type();
		return type.equals(typeFactory.BOOLEAN_PRIMITIVE);
	}

	private boolean isOneArgInvoke(CtTypedElement<?> candidate) {
		var parent = candidate.getParent();
		if (parent instanceof CtInvocation) {
			var args = ((CtInvocation<?>) parent).getArguments();
			return args.size() == 1;
		}
		return false;
	}

	private boolean isCondAssign(CtTypedElement<?> candidate) {
		return (candidate.getParent() instanceof CtAssignment || candidate.getParent() instanceof CtReturn)
				&& (candidate.getParent(CtIf.class) != null || candidate.getParent(CtSwitch.class) != null);
	}
}
