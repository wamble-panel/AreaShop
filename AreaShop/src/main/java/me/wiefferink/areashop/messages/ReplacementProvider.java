package me.wiefferink.areashop.messages;

/**
 * Something that can fill in named variables like {@code %region%} in a message.
 */
public interface ReplacementProvider {

	/**
	 * Provide the value for a variable.
	 * @param variable Name of the variable, without the surrounding percent signs
	 * @return The value to insert, or null when this provider does not know the variable
	 */
	Object provideReplacement(String variable);
}
