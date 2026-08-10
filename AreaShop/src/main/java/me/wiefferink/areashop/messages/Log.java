package me.wiefferink.areashop.messages;

import java.util.logging.Logger;

/**
 * Logging used by the message framework, wired up to the plugin logger on startup.
 */
public final class Log {

	private static Logger logger = Logger.getLogger("AreaShop");

	private Log() {
	}

	/**
	 * Set the logger to use.
	 * @param logger The logger of the plugin
	 */
	public static void setLogger(Logger logger) {
		Log.logger = logger;
	}

	public static void info(String message) {
		logger.info(message);
	}

	public static void warn(String message) {
		logger.warning(message);
	}

	public static void error(String message) {
		logger.severe(message);
	}
}
