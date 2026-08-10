package me.wiefferink.areashop;

import me.wiefferink.areashop.tools.Expression;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the price expressions that can be used in config.yml, like {@code %volume% * 0.5}.
 */
class ExpressionTest {

	private static final double PRECISION = 0.000001;

	@Test
	void respectsOperatorPrecedence() {
		assertEquals(14, Expression.evaluate("2 + 3 * 4"), PRECISION);
		assertEquals(20, Expression.evaluate("(2 + 3) * 4"), PRECISION);
		assertEquals(-2, Expression.evaluate("-3 + 1"), PRECISION);
		assertEquals(3, Expression.evaluate("7 % 4"), PRECISION);
	}

	@Test
	void raisingToAPowerBindsToTheRight() {
		assertEquals(512, Expression.evaluate("2^3^2"), PRECISION);
	}

	@Test
	void readsNumbers() {
		assertEquals(12, Expression.evaluate("  12  "), PRECISION);
		assertEquals(500, Expression.evaluate("1000 * 0.5"), PRECISION);
		assertEquals(1500, Expression.evaluate("1.5e3"), PRECISION);
	}

	@Test
	void supportsFunctions() {
		assertEquals(2, Expression.evaluate("max(1, 2)"), PRECISION);
		assertEquals(3, Expression.evaluate("min(3, 4)"), PRECISION);
		assertEquals(2, Expression.evaluate("round(1.6)"), PRECISION);
		assertEquals(4, Expression.evaluate("sqrt(16)"), PRECISION);
	}

	@Test
	void refusesDivisionByZero() {
		assertThrows(Expression.ExpressionException.class, () -> Expression.evaluate("1 / 0"));
	}

	@Test
	void refusesAnythingThatIsNotArithmetic() {
		// Prices used to be handed to a JavaScript engine, which could run whatever it was given
		assertThrows(Expression.ExpressionException.class, () -> Expression.evaluate("java.lang.System.exit(0)"));
		assertThrows(Expression.ExpressionException.class, () -> Expression.evaluate("'a' + 'b'"));
	}

	@Test
	void refusesVariablesThatWereNeverFilledIn() {
		assertThrows(Expression.ExpressionException.class, () -> Expression.evaluate("%price% * 2"));
	}

	@Test
	void refusesEmptyInput() {
		assertThrows(Expression.ExpressionException.class, () -> Expression.evaluate(""));
		assertThrows(Expression.ExpressionException.class, () -> Expression.evaluate(null));
	}
}
