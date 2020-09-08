package edu.purdue.dsnl.configprof.filter;

import spoon.reflect.code.*;
import spoon.reflect.declaration.CtEnumValue;
import spoon.reflect.declaration.CtTypedElement;
import spoon.reflect.declaration.CtVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

class EnumParamProcessor extends AbstractParamProcessor<CtFieldRead<?>> {
	private static final Pattern CAPITALIZED = Pattern.compile("[A-Z][A-Z0-9_]+[A-Z0-9]");

	EnumParamProcessor(List<String> sources) {
		super(sources);
	}

	@Override
	public boolean isToBeProcessed(CtFieldRead<?> candidate) {
		if (!isEnum(candidate) || !isCapitalized(candidate)) {
			return false;
		}
		if (isEnabled(FilterLevel.CATEGORY) && !(isInit(candidate) || isArg(candidate))) {
			return false;
		}
		if (isEnabled(FilterLevel.HEURISTIC) && isIgnoreVarClass(candidate)) {
			return false;
		}
		if (isEnabled(FilterLevel.ALL)
				&& (isCondAssign(candidate) || isCondition(candidate) || isSwitchCase(candidate))) {
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
	public void process(CtFieldRead<?> element) {
		if (element.getType() == null) {
			System.out.println(element.getVariable().getQualifiedName());
		}
		super.process(element);
	}

	@Override
	protected boolean isCompatVarType(CtVariable<?> variable) {
		/*
		 * The only type of variables missing is those framework or 3rd party fields.
		 * But it is rare that those fields can be directly assigned.
		 */
		return variable instanceof CtEnumValue;
	}

	private boolean isEnum(CtFieldRead<?> candidate) {
		var variable = candidate.getVariable().getDeclaration();
		return variable == null || variable instanceof CtEnumValue;
	}

	private boolean isCapitalized(CtVariableRead<?> candidate) {
		return CAPITALIZED.matcher(candidate.getVariable().getSimpleName()).matches();
	}

	private boolean isCondAssign(CtTypedElement<?> candidate) {
		return (candidate.getParent() instanceof CtAssignment || candidate.getParent() instanceof CtReturn)
				&& (candidate.getParent(CtIf.class) != null || candidate.getParent(CtSwitch.class) != null);
	}

	private boolean isCondition(CtTypedElement<?> candidate) {
		if (candidate.getParent() instanceof CtConditional) {
			return true;
		}
		var ifStmt = candidate.getParent(CtIf.class);
		return ifStmt != null && equalOrParent(ifStmt.getCondition(), candidate);
	}

	private boolean isSwitchCase(CtTypedElement<?> candidate) {
		var switchCase = candidate.getParent(CtCase.class);
		return switchCase != null && equalOrParent(switchCase.getCaseExpression(), candidate);
	}

	private boolean isIgnoreVarClass(CtTypedElement<?> candidate) {
		var type = candidate.getType();
		return type != null && "java.util.concurrent.TimeUnit".equals(type.getQualifiedName());
	}
}
