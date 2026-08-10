package me.wiefferink.areashop.tools;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Scheduling helpers, mostly to spread heavy work over multiple ticks.
 */
public final class Do {

	private static Plugin plugin;

	private Do() {
	}

	/**
	 * Hook up the scheduling helpers to the plugin.
	 * @param plugin The plugin to schedule tasks for
	 */
	public static void init(Plugin plugin) {
		Do.plugin = plugin;
	}

	/**
	 * Run something on the next tick.
	 * @param what The work to do
	 * @return The scheduled task
	 */
	public static BukkitTask sync(Runnable what) {
		return Bukkit.getScheduler().runTask(plugin, what);
	}

	/**
	 * Run something after a delay.
	 * @param delay Ticks to wait
	 * @param what  The work to do
	 * @return The scheduled task
	 */
	public static BukkitTask syncLater(long delay, Runnable what) {
		return Bukkit.getScheduler().runTaskLater(plugin, what, Math.max(1, delay));
	}

	/**
	 * Run something over and over again.
	 * @param period Ticks between runs
	 * @param what   The work to do
	 * @return The scheduled task
	 */
	public static BukkitTask syncTimer(long period, Runnable what) {
		return syncTimer(period, period, what);
	}

	/**
	 * Run something over and over again.
	 * @param delay  Ticks to wait before the first run
	 * @param period Ticks between runs
	 * @param what   The work to do
	 * @return The scheduled task
	 */
	public static BukkitTask syncTimer(long delay, long period, Runnable what) {
		return Bukkit.getScheduler().runTaskTimer(plugin, what, Math.max(1, delay), Math.max(1, period));
	}

	/**
	 * Run something over and over again until it says it is done.
	 * @param delay  Ticks to wait before the first run
	 * @param period Ticks between runs
	 * @param what   The work to do, returning true to be run again and false to stop
	 * @return The scheduled task
	 */
	public static BukkitTask syncTimerLater(long delay, long period, Supplier<Boolean> what) {
		BukkitRunnable runnable = new BukkitRunnable() {
			@Override
			public void run() {
				if(!Boolean.TRUE.equals(what.get())) {
					cancel();
				}
			}
		};
		return runnable.runTaskTimer(plugin, Math.max(1, delay), Math.max(1, period));
	}

	/**
	 * Do something with all items, spreading the work over multiple ticks.
	 * @param items The items to handle
	 * @param what  The work to do per item
	 * @param <T>   Type of the items
	 */
	public static <T> void forAll(Collection<T> items, Consumer<T> what) {
		forAll(1, items, what, null);
	}

	/**
	 * Do something with all items, spreading the work over multiple ticks.
	 * @param perTick How many items to handle each tick
	 * @param items   The items to handle
	 * @param what    The work to do per item
	 * @param <T>     Type of the items
	 */
	public static <T> void forAll(int perTick, Collection<T> items, Consumer<T> what) {
		forAll(perTick, items, what, null);
	}

	/**
	 * Do something with all items, spreading the work over multiple ticks.
	 * @param perTick  How many items to handle each tick, zero or less handles them all at once
	 * @param items    The items to handle
	 * @param what     The work to do per item
	 * @param complete Run when all items have been handled, may be null
	 * @param <T>      Type of the items
	 */
	public static <T> void forAll(int perTick, Collection<T> items, Consumer<T> what, Runnable complete) {
		// Work on a copy, the work itself is allowed to change the original collection
		List<T> todo = items == null ? List.of() : new ArrayList<>(items);

		if(perTick <= 0 || todo.size() <= perTick) {
			for(T item : todo) {
				accept(what, item);
			}
			if(complete != null) {
				complete.run();
			}
			return;
		}

		Iterator<T> iterator = todo.iterator();
		new BukkitRunnable() {
			@Override
			public void run() {
				for(int done = 0; done < perTick && iterator.hasNext(); done++) {
					accept(what, iterator.next());
				}
				if(!iterator.hasNext()) {
					cancel();
					if(complete != null) {
						complete.run();
					}
				}
			}
		}.runTaskTimer(plugin, 1, 1);
	}

	/**
	 * Handle one item, making sure a failure does not take down the whole batch.
	 * @param what The work to do
	 * @param item The item to handle
	 * @param <T>  Type of the item
	 */
	private static <T> void accept(Consumer<T> what, T item) {
		try {
			what.accept(item);
		} catch(RuntimeException e) {
			plugin.getLogger().warning("Failed to handle " + item + ": " + Utils.getStackTrace(e));
		}
	}
}
