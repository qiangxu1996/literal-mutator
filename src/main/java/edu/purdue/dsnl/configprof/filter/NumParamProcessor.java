package edu.purdue.dsnl.configprof.filter;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtTypedElement;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.visitor.Filter;

import java.util.ArrayList;
import java.util.List;

class NumParamProcessor extends AbstractParamProcessor<CtLiteral<?>> {
	private final List<Pair<String, Integer>> ignoreMethods = List.of(
			new ImmutablePair<>("add", 0), new ImmutablePair<>("addAll", 0),
			new ImmutablePair<>("get", 0), new ImmutablePair<>("listIterator", 0),
			new ImmutablePair<>("remove", 0), new ImmutablePair<>("substring", 0),
			new ImmutablePair<>("substring", 1), new ImmutablePair<>("getDoubleExtra", 1),
			new ImmutablePair<>("getFloatExtra", 1), new ImmutablePair<>("getIntExtra", 1),
			new ImmutablePair<>("getLongExtra", 1), new ImmutablePair<>("getShortExtra", 1),
			new ImmutablePair<>("getDouble", 1), new ImmutablePair<>("getFloat", 1),
			new ImmutablePair<>("getInt", 1), new ImmutablePair<>("getLong", 1),
			new ImmutablePair<>("getShort", 1), new ImmutablePair<>("fill", 1),
			new ImmutablePair<>("charAt", 0), new ImmutablePair<>("notify", 0),
			new ImmutablePair<>("getChildAt", 0), new ImmutablePair<>("getSharedPreferences", 1));

	NumParamProcessor(List<String> sources) {
		super(sources);
	}

	@Override
	public boolean isToBeProcessed(CtLiteral<?> candidate) {
		if (!isNumeric(candidate)) {
			return false;
		}
		if (isEnabled(FilterLevel.CATEGORY) && !(isInit(candidate) || isArg(candidate))) {
			return false;
		}
		if (isEnabled(FilterLevel.HEURISTIC) && (isCompareWithZeroOne(candidate) || isPlusOrMinusOne(candidate))) {
			return false;
		}
		if (isEnabled(FilterLevel.ALL)
				&& (isZeroArray(candidate) || isEqualCompare(candidate)
				|| isReturnZeroOne(candidate) || isBitOp(candidate))) {
			return false;
		}

		var toBeChecked = new ArrayList<CtTypedElement<?>>();
		toBeChecked.add(candidate);

		var variable = getVariable(candidate);
		if (variable != null) {
			if (isEnabled(FilterLevel.HEURISTIC) && isIgnoreVariable(variable)) {
				return false;
			}
			var accesses = getVariableAccesses(variable);
			if (isEnabled(FilterLevel.HEURISTIC) && isMultiWrite(accesses)) {
				return false;
			}
			accesses.removeIf(this::isWriteAccess);
			toBeChecked.addAll(accesses);
		}

		for (var el : toBeChecked) {
			if (isEnabled(FilterLevel.HEURISTIC)
					&& (isForInit(el) || isIgnoreMethod(el) || isIgnoreClass(el) || isIgnoreMethodParam(el))) {
				return false;
			}
			if (isEnabled(FilterLevel.ALL)
					&& (isIncrement(el) || isArrayIndex(el) || isSwitchCase(el) || isSdkCompare(el))) {
				return false;
			}
		}

		return toBeChecked.stream().anyMatch(this::isCovered);
	}

	@Override
	public void process(CtLiteral<?> element) {
		super.process(element);
	}

	@Override
	protected boolean isCompatVarType(CtVariable<?> variable) {
		return isNumeric(variable);
	}

	private boolean isNumeric(CtTypedElement<?> candidate) {
		var type = candidate.getType().unbox();
		var typeFactory = getFactory().Type();
		return type.equals(typeFactory.DOUBLE_PRIMITIVE)
				|| type.equals(typeFactory.FLOAT_PRIMITIVE)
				|| type.equals(typeFactory.INTEGER_PRIMITIVE)
				|| type.equals(typeFactory.LONG_PRIMITIVE)
				|| type.equals(typeFactory.SHORT_PRIMITIVE);
	}

	private boolean isCompareWithZeroOne(CtLiteral<?> candidate) {
		var value = candidate.getValue();
		if (value.equals(0) || value.equals(1)) {
			var binOp = candidate.getParent((Filter<CtBinaryOperator<?>>) element -> {
				var op = element.getKind();
				return op == BinaryOperatorKind.EQ || op == BinaryOperatorKind.GE || op == BinaryOperatorKind.GT
						|| op == BinaryOperatorKind.LE || op == BinaryOperatorKind.LT || op == BinaryOperatorKind.NE;
			});
			return binOp != null && noInvokeInBetween(binOp, candidate);
		}
		return false;
	}

	private boolean isPlusOrMinusOne(CtLiteral<?> candidate) {
		var value = candidate.getValue();
		if (value.equals(1) || value.equals(2)) {
			var parent = candidate.getParent();
			if (parent instanceof CtBinaryOperator) {
				var op = ((CtBinaryOperator<?>) parent).getKind();
				return op == BinaryOperatorKind.MINUS || op == BinaryOperatorKind.PLUS;
			}
		}
		return false;
	}

	private boolean isZeroArray(CtLiteral<?> candidate) {
		return candidate.getValue().equals(0) && candidate.getParent() instanceof CtNewArray;
	}

	private boolean isEqualCompare(CtTypedElement<?> candidate) {
		var binOp = candidate.getParent((Filter<CtBinaryOperator<?>>) element -> {
			var op = element.getKind();
			return op == BinaryOperatorKind.EQ || op == BinaryOperatorKind.NE;
		});
		return binOp != null && noInvokeInBetween(binOp, candidate);
	}

	private boolean isReturnZeroOne(CtLiteral<?> candidate) {
		var value = candidate.getValue();
		if (value.equals(0) || value.equals(1)) {
			var parent = candidate.getParent();
			if (parent instanceof CtUnaryOperator) {
				parent = parent.getParent();
			}
			return parent instanceof CtReturn;
		}
		return false;
	}

	private boolean isShift(BinaryOperatorKind op) {
		return op == BinaryOperatorKind.SL || op == BinaryOperatorKind.SR || op == BinaryOperatorKind.USR;
	}

	private boolean isBitOp(CtTypedElement<?> candidate) {
		var binOp = candidate.getParent(element -> {
			BinaryOperatorKind op = null;
			if (element instanceof CtBinaryOperator) {
				op = ((CtBinaryOperator<?>) element).getKind();
			} else if (element instanceof CtOperatorAssignment) {
				op = ((CtOperatorAssignment<?, ?>) element).getKind();
			}
			return op == BinaryOperatorKind.BITAND || op == BinaryOperatorKind.BITOR || op == BinaryOperatorKind.BITXOR
					|| isShift(op);
		});

		if (binOp != null && noInvokeInBetween(binOp, candidate)) {
			BinaryOperatorKind op;
			if (binOp instanceof CtBinaryOperator) {
				var optr = (CtBinaryOperator<?>) binOp;
				op = optr.getKind();
				if (isShift(op) && equalOrParent(optr.getRightHandOperand(), candidate)) {
					return true;
				}
			} else {
				var optr = (CtOperatorAssignment<?, ?>) binOp;
				op = optr.getKind();
				if (isShift(op) && equalOrParent(optr.getAssignment(), candidate)) {
					return true;
				}
			}
			return !isShift(op);
		}

		return false;
	}

	private boolean isIgnoreVariable(CtVariable<?> candidate) {
		var name = candidate.getSimpleName().toLowerCase();
		return name.endsWith("color") || name.endsWith("colour");
	}

	private boolean isIncrement(CtTypedElement<?> candidate) {
		var parent = candidate.getParent();
		if (parent instanceof CtUnaryOperator) {
			var op = ((CtUnaryOperator<?>) parent).getKind();
			return op != UnaryOperatorKind.NEG && op != UnaryOperatorKind.POS;
		}
		return false;
	}

	private boolean isArrayIndex(CtTypedElement<?> candidate) {
		var arrayAccess = candidate.getParent(CtArrayAccess.class);
		return arrayAccess != null && noInvokeInBetween(arrayAccess, candidate);
	}

	private boolean isArrayInitVal(CtTypedElement<?> candidate) {
		CtNewArray<?> newArray = candidate.getParent(CtNewArray.class);
		if (newArray != null && noInvokeInBetween(newArray, candidate)) {
			return newArray.getElements().stream().anyMatch(i -> equalOrParent(i, candidate));
		}
		return false;
	}

	private boolean isSwitchCase(CtTypedElement<?> candidate) {
		var switchCase = candidate.getParent(CtCase.class);
		return switchCase != null && equalOrParent(switchCase.getCaseExpression(), candidate);
	}

	private boolean isForInit(CtTypedElement<?> candidate) {
		var forLoop = candidate.getParent(CtFor.class);
		if (forLoop != null) {
			var forInits = forLoop.getForInit();
			for (var forInit : forInits) {
				if (equalOrParent(forInit, candidate)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isSdkCompare(CtTypedElement<?> candidate) {
		var parent = candidate.getParent();
		if (parent instanceof CtBinaryOperator) {
			var lh = ((CtBinaryOperator<?>) parent).getLeftHandOperand();
			if (lh instanceof CtFieldRead) {
				var field = ((CtFieldRead<?>) lh).getVariable().getSimpleName();
				return "SDK_INT".equals(field);
			}
		}
		return false;
	}

	private boolean isIgnoreMethod(CtTypedElement<?> candidate) {
		var methodRef = candidate.getParent(element -> {
			if (element instanceof CtInvocation) {
				var methodName = ((CtInvocation<?>) element).getExecutable().getSimpleName().toLowerCase();
				return methodName.endsWith("color") || methodName.endsWith("index") || methodName.endsWith("id")
						|| "setmargins".equals(methodName) || "setbounds".equals(methodName)
						|| "setspan".equals(methodName) || "setpadding".equals(methodName);
			}
			return false;
		});
		return methodRef != null;
	}

	private boolean isIgnoreClass(CtTypedElement<?> candidate) {
		var method = candidate.getParent(CtInvocation.class);
		if (method != null) {
			var type = method.getExecutable().getDeclaringType();
			if (type != null) {
				var className = type.toString();
				return "android.graphics.Canvas".equals(className) || "android.graphics.Path".equals(className)
						|| "android.app.PendingIntent".equals(className);
			}
		}
		return false;
	}

	private boolean isIgnoreMethodParam(CtTypedElement<?> candidate) {
		CtInvocation<?> method = candidate.getParent(CtInvocation.class);
		if (method != null) {
			var name = method.getExecutable().getSimpleName();
			var args = method.getArguments();
			for (var m : ignoreMethods) {
				if (name.equals(m.getLeft())) {
					int argIndex = m.getRight();
					if (argIndex < args.size() && equalOrParent(args.get(argIndex), candidate)) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
