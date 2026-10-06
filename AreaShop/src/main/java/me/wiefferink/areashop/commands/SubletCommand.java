package me.wiefferink.areashop.commands;

import me.wiefferink.areashop.features.SubletFeature;
import me.wiefferink.areashop.gui.SubletMarketGui;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.tools.Utils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Rent space in a region out to other players, and rent space from them.
 */
public class SubletCommand extends CommandAreaShop {

	/** Word that stops a region being offered. */
	private static final String STOP = "stop";

	@Override
	public String getCommandStart() {
		return "areashop sublet";
	}

	@Override
	public String getHelp(CommandSender target) {
		if(target.hasPermission("areashop.sublet") || target.hasPermission("areashop.subrent")) {
			return "help-sublet";
		}
		return null;
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		if(!(sender instanceof Player player)) {
			plugin.message(sender, "cmd-onlyByPlayer");
			return;
		}

		// Without arguments, show what is on offer
		if(args.length < 2) {
			if(!player.hasPermission("areashop.subrent")) {
				plugin.message(sender, "sublet-noPermissionRent");
				return;
			}
			new SubletMarketGui(player).open();
			return;
		}

		if(!player.hasPermission("areashop.sublet")) {
			plugin.message(sender, "sublet-noPermission");
			return;
		}

		GeneralRegion region = plugin.getFileManager().getRegion(args[1]);
		if(region == null) {
			plugin.message(sender, "sublet-notRegistered", args[1]);
			return;
		}
		if(!region.isOwner(player)) {
			plugin.message(sender, "sublet-notYours", region);
			return;
		}

		SubletFeature sublet = region.getSubletFeature();

		if(args.length == 3 && STOP.equalsIgnoreCase(args[2])) {
			sublet.stopOffering();
			plugin.message(sender, "sublet-stopped", region);
			return;
		}

		if(args.length < 4) {
			plugin.message(sender, "sublet-help");
			return;
		}

		double price;
		try {
			price = Double.parseDouble(args[2]);
		} catch(NumberFormatException e) {
			plugin.message(sender, "sublet-wrongPrice", args[2]);
			return;
		}
		if(price < 0) {
			plugin.message(sender, "sublet-wrongPrice", args[2]);
			return;
		}

		// Everything after the price is the duration, so '7 days' works as written
		String duration = Utils.join(args, " ", 3, args.length).trim();
		if(!Utils.checkTimeFormat(duration)) {
			plugin.message(sender, "sublet-wrongDuration", duration);
			return;
		}

		sublet.offer(price, duration);
		region.update();
		plugin.message(sender, "sublet-offering", region, Utils.formatCurrency(price), duration);
	}

	@Override
	public List<String> getTabCompleteList(int toComplete, String[] start, CommandSender sender) {
		List<String> result = new ArrayList<>();
		if(!(sender instanceof Player player) || !sender.hasPermission("areashop.sublet")) {
			return result;
		}
		if(toComplete == 2) {
			for(GeneralRegion region : plugin.getFileManager().getRegions()) {
				if(region.isOwner(player)) {
					result.add(region.getName());
				}
			}
		} else if(toComplete == 3) {
			result.add(STOP);
		}
		return result;
	}

}
