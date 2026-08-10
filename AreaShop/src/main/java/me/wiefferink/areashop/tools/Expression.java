package me.wiefferink.areashop.tools;

import java.util.Locale;

/**
 * Evaluates the math expressions that can be used for prices in config.yml, like
 * {@code %volume% * 0.5 + 100}.
 *
 * <p>AreaShop used to hand these to the JavaScript engine that shipped with Java 8, which has been
 * gone since Java 15. This evaluator understands the same arithmetic, but nothing else, so a price
 * setting can no longer run code on the server.
 *
 * <p>Supported: the {@code + - * / % ^} operators, unary minus, parentheses, and the
 * {@code min}, {@code max}, {@code abs}, {@code floor}, {@code ceil}, {@code round} and
 * {@code sqrt} functions.
 */
public final class Expression {

	private final String input;
	private int position;

	private Expression(String input) {
		this.input = input;
	}

	/**
	 * Thrown when an expression cannot be evaluated.
	 */
	public static class ExpressionException extends RuntimeException {
		public ExpressionException(String message) {
			super(message);
		}
	}

	/**
	 * Evaluate a math expression.
	 * @param input The expression to evaluate
	 * @return The resulting number
	 * @throws ExpressionException When the expression is not valid
	 */
	public static double evaluate(String input) {
		if(input == null || input.isBlank()) {
			throw new ExpressionException("the expression is empty");
		}
		Expression expression = new Expression(input);
		double result = expression.parseSum();
		expression.skipWhitespace();
		if(expression.position < input.length()) {
			throw new ExpressionException("unexpected '" + input.charAt(expression.position) + "' at position " + expression.position);
		}
		if(Double.isNaN(result)) {
			throw new ExpressionException("the expression does not result in a number");
		}
		return result;
	}

	private void skipWhitespace() {
		while(position < input.length() && Character.isWhitespace(input.charAt(position))) {
			position++;
		}
	}

	/**
	 * Consume the given operator when it is the next thing in the input.
	 * @param operator The operator to look for
	 * @return true when it was consumed
	 */
	private boolean eat(char operator) {
		skipWhitespace();
		if(position < input.length() && input.charAt(position) == operator) {
			position++;
			return true;
		}
		return false;
	}

	/** Addition and subtraction, the loosest binding operators. */
	private double parseSum() {
		double result = parseProduct();
		while(true) {
			if(eat('+')) {
				result += parseProduct();
			} else if(eat('-')) {
				result -= parseProduct();
			} else {
				return result;
			}
		}
	}

	/** Multiplication, division and remainder. */
	private double parseProduct() {
		double result = parsePower();
		while(true) {
			if(eat('*')) {
				result *= parsePower();
			} else if(eat('/')) {
				double divisor = parsePower();
				if(divisor == 0) {
					throw new ExpressionException("division by zero");
				}
				result /= divisor;
			} else if(eat('%')) {
				double divisor = parsePower();
				if(divisor == 0) {
					throw new ExpressionException("division by zero");
				}
				result %= divisor;
			} else {
				return result;
			}
		}
	}

	/** Exponentiation, which binds to the right: 2^3^2 is 2^(3^2). */
	private double parsePower() {
		double base = parseUnary();
		if(eat('^')) {
			return Math.pow(base, parsePower());
		}
		return base;
	}

	/** A sign in front of a value. */
	private double parseUnary() {
		if(eat('-')) {
			return -parseUnary();
		}
		if(eat('+')) {
			return parseUnary();
		}
		return parseValue();
	}

	/** A number, a bracketed expression, or a function call. */
	private double parseValue() {
		skipWhitespace();
		if(position >= input.length()) {
			throw new ExpressionException("the expression ends too early");
		}

		if(eat('(')) {
			double result = parseSum();
			if(!eat(')')) {
				throw new ExpressionException("missing a closing bracket");
			}
			return result;
		}

		char current = input.charAt(position);
		if(Character.isDigit(current) || current == '.') {
			int start = position;
			while(position < input.length() && (Character.isDigit(input.charAt(position)) || input.charAt(position) == '.')) {
				position++;
			}
			// Scientific notation, like 1.5e3
			if(position < input.length() && (input.charAt(position) == 'e' || input.charAt(position) == 'E')) {
				int exponent = position + 1;
				if(exponent < input.length() && (input.charAt(exponent) == '+' || input.charAt(exponent) == '-')) {
					exponent++;
				}
				if(exponent < input.length() && Character.isDigit(input.charAt(exponent))) {
					position = exponent;
					while(position < input.length() && Character.isDigit(input.charAt(position))) {
						position++;
					}
				}
			}
			try {
				return Double.parseDouble(input.substring(start, position));
			} catch(NumberFormatException e) {
				throw new ExpressionException("'" + input.substring(start, position) + "' is not a number");
			}
		}

		if(Character.isLetter(current)) {
			int start = position;
			while(position < input.length() && Character.isLetter(input.charAt(position))) {
				position++;
			}
			return parseFunction(input.substring(start, position).toLowerCase(Locale.ROOT));
		}

		throw new ExpressionException("unexpected '" + current + "' at position " + position);
	}

	/**
	 * Evaluate a function call, the name has already been read.
	 * @param name Name of the function
	 * @return The result of the function
	 */
	private double parseFunction(String name) {
		if(!eat('(')) {
			throw new ExpressionException("'" + name + "' is not a number, use a variable that AreaShop knows");
		}
		double first = parseSum();
		Double second = null;
		if(eat(',')) {
			second = parseSum();
		}
		if(!eat(')')) {
			throw new ExpressionException("missing a closing bracket after '" + name + "'");
		}

		return switch(name) {
			case "min" -> requireTwo(name, second) ? Math.min(first, second) : first;
			case "max" -> requireTwo(name, second) ? Math.max(first, second) : first;
			case "abs" -> Math.abs(first);
			case "floor" -> Math.floor(first);
			case "ceil" -> Math.ceil(first);
			case "round" -> (double)Math.round(first);
			case "sqrt" -> Math.sqrt(first);
			default -> throw new ExpressionException("unknown function '" + name + "'");
		};
	}

	private static boolean requireTwo(String name, Double second) {
		if(second == null) {
			throw new ExpressionException("'" + name + "' needs two arguments, like " + name + "(1, 2)");
		}
		return true;
	}
}
