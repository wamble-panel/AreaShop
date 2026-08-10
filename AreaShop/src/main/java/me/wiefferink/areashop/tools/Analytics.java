package me.wiefferink.areashop.tools;

import me.wiefferink.areashop.AreaShop;
import me.wiefferink.areashop.regions.BuyRegion;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.regions.RentRegion;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;

import java.util.HashMap;
import java.util.Map;

public final class Analytics {

	/**
	 * Service id of AreaShop on bstats.org.
	 * bStats 2 and up identify a plugin by this number instead of by its name, it has to match the
	 * id shown on the plugin page at https://bstats.org, otherwise the data ends up nowhere.
	 * Set 'bstatsId' in hiddenConfig.yml to override it.
	 */
	private static final int DEFAULT_BSTATS_ID = 3204;

	private Analytics() {

	}

	/**
	 * Start analytics tracking.
	 */
	public static void start() {
		// bStats statistics
		try {
			int pluginId = AreaShop.getInstance().getConfig().getInt("bstatsId", DEFAULT_BSTATS_ID);
			Metrics metrics = new Metrics(AreaShop.getInstance(), pluginId);

			// Number of regions
			metrics.addCustomChart(new SingleLineChart("region_count",
					() -> AreaShop.getInstance().getFileManager().getRegions().size()));

			// Number of rental regions
			metrics.addCustomChart(new SingleLineChart("rental_region_count",
					() -> AreaShop.getInstance().getFileManager().getRents().size()));

			// Number of buy regions
			metrics.addCustomChart(new SingleLineChart("buy_region_count",
					() -> AreaShop.getInstance().getFileManager().getBuys().size()));

			// Language
			metrics.addCustomChart(new SimplePie("language",
					() -> AreaShop.getInstance().getConfig().getString("language")));

			// Pie with region states
			metrics.addCustomChart(new AdvancedPie("region_state", () -> {
				RegionStateStats stats = getStateStats();
				Map<String, Integer> result = new HashMap<>();
				result.put("For Rent", stats.forrent);
				result.put("Rented", stats.rented);
				result.put("For Sale", stats.forsale);
				result.put("Sold", stats.sold);
				result.put("Reselling", stats.reselling);
				return result;
			}));

			// Time series of each region state
			metrics.addCustomChart(new SingleLineChart("forrent_region_count", () -> getStateStats().forrent));
			metrics.addCustomChart(new SingleLineChart("rented_region_count", () -> getStateStats().rented));
			metrics.addCustomChart(new SingleLineChart("forsale_region_count", () -> getStateStats().forsale));
			metrics.addCustomChart(new SingleLineChart("sold_region_count", () -> getStateStats().sold));
			metrics.addCustomChart(new SingleLineChart("reselling_region_count", () -> getStateStats().reselling));

			AreaShop.debug("Started bstats.org statistics service");
		} catch(Exception e) {
			AreaShop.debug("Could not start bstats.org statistics service:", Utils.getStackTrace(e));
		}
	}

	private static class RegionStateStats {
		int forrent = 0;
		int forsale = 0;
		int rented = 0;
		int sold = 0;
		int reselling = 0;
	}

	private static RegionStateStats getStateStats() {
		RegionStateStats result = new RegionStateStats();
		for(GeneralRegion region : AreaShop.getInstance().getFileManager().getRegions()) {
			if(region instanceof RentRegion rent) {
				if(rent.isAvailable()) {
					result.forrent++;
				} else {
					result.rented++;
				}
			} else if(region instanceof BuyRegion buy) {
				if(buy.isAvailable()) {
					result.forsale++;
				} else if(buy.isInResellingMode()) {
					result.reselling++;
				} else {
					result.sold++;
				}
			}
		}
		return result;
	}

}
