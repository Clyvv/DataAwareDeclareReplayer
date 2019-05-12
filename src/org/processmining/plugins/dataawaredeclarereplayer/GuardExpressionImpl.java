package org.processmining.plugins.dataawaredeclarereplayer;

import java.util.Set;

import org.processmining.datapetrinets.exception.EvaluatorException;
import org.processmining.datapetrinets.expression.AbstractGuardExpression;
import org.processmining.datapetrinets.expression.Evaluator;
import org.processmining.datapetrinets.expression.FunctionProvider;
import org.processmining.datapetrinets.expression.LiteralValueCollector;
import org.processmining.datapetrinets.expression.Printer;
import org.processmining.datapetrinets.expression.VariableCollector;
import org.processmining.datapetrinets.expression.VariableProvider;
import org.processmining.datapetrinets.expression.syntax.ExprRoot;
import org.processmining.datapetrinets.expression.syntax.ExpressionParser;
import org.processmining.datapetrinets.expression.syntax.ExpressionParserVisitor;
import org.processmining.datapetrinets.expression.syntax.ExpressionVisitorException;
import org.processmining.datapetrinets.expression.syntax.ParseException;

public class GuardExpressionImpl extends AbstractGuardExpression {

	private final ExprRoot expression;

	private final Set<String> normalVariable;
	private final Set<String> primeVariables;
	private final String canonicalString;

	GuardExpressionImpl(String expression) throws ParseException {
		this(new ExpressionParser(expression).parse());
	}

	GuardExpressionImpl(ExprRoot expression) {
		super();
		this.expression = expression;
		this.primeVariables = VariableCollector.collectPrimesOnly(expression);
		this.normalVariable = VariableCollector.collectNormalOnly(expression);
		this.canonicalString = Printer.printCanonical(expression);
	}

	public ExprRoot getExpression() {
		return expression;
	}

	@Override
	public Object evaluate(VariableProvider variableProvider, FunctionProvider functionProvider)
			throws EvaluatorException {
		return Evaluator.evaluate(this, variableProvider, functionProvider);
	}

	@Override
	public Set<String> getNormalVariables() {
		return normalVariable;
	}

	@Override
	public Set<String> getPrimeVariables() {
		return primeVariables;
	}

	@Override
	public <T> Set<T> getLiteralValues(Class<T> type) {
		return LiteralValueCollector.collectAll(expression, type);
	}

	@Override
	public Object visit(ExpressionParserVisitor visitor, Object data) throws ExpressionVisitorException {
		return visitor.visit(expression, data);
	}

	@Override
	public String toCanonicalString() {
		return canonicalString;
	}

	@Override
	public String toPrettyString(int spaces) {
		return Printer.printPretty(expression, spaces);
	}

	@Override
	public String toTreeLikeString(int indent) {
		return Printer.printTree(expression, indent);
	}

	public String toString() {
		return toCanonicalString();
	}

	//TODO: equals and hashCode based on the canonical form might be not the most efficient way 

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((canonicalString == null) ? 0 : canonicalString.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof GuardExpressionImpl))
			return false;
		GuardExpressionImpl other = (GuardExpressionImpl) obj;
		if (canonicalString == null) {
			if (other.canonicalString != null)
				return false;
		} else if (!canonicalString.equals(other.canonicalString))
			return false;
		return true;
	}
}
