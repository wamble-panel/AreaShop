package me.wiefferink.areashop.commands;

import me.wiefferink.areashop.features.RentOutFeature;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.tools.Utils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Rent a shop you hold out to another player.
 *
 * <p>Taking one that someone else is renting out is {@code /as rentfrom}, so each command does one
 * thing and reads as what it does.
 */
public class RentOutCommand extends CommandAreaShop {

	/** Word that stops a region being offered. */
	private static final String STOP = "stop";

	@Override
	public String getCommandStart() {
		return "areashop rentout";
	}

	@Override
	public String getHelp(CommandSender target) {
		if(target.hasPermission("areashop.rentout")) {
			return "help-rentout";
		}
		return null;
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		if(!(sender instanceof Player player)) {
			plugin.message(sender, "cmd-onlyByPlayer");
			return;
		}
		if(!player.hasPermission("areashop.rentout")) {
			plugin.message(sender, "rentout-noPermission");
			return;
		}
		if(args.length < 2) {
			plugin.message(sender, "rentout-help");
			return;
		}

		GeneralRegion region = plugin.getFileManager().getRegion(args[1]);
		if(region == null) {
			plugin.message(sender, "rentout-notRegistered", args[1]);
			return;
		}
		if(!region.isOwner(player)) {
			plugin.message(sender, "rentout-notYours", region);
			return;
		}

		RentOutFeature rentOut = region.getRentOutFeature();

		if(args.length == 3 && STOP.equalsIgnoreCase(args[2])) {
			rentOut.stopOffering();
			plugin.message(sender, "rentout-stopped", region);
			return;
		}

		if(args.length < 4) {
			plugin.message(sender, "rentout-help");
			return;
		}

		double price;
		try {
			price = Double.parseDouble(args[2]);
		} catch(NumberFormatException e) {
			plugin.message(sender, "rentout-wrongPrice", args[2]);
			return;
		}
		if(price < 0) {
			plugin.message(sender, "rentout-wrongPrice", args[2]);
			return;
		}

		// Everything after the price is the duration, so '7 days' works as written
		String duration = Utils.join(args, " ", 3, args.length).trim();
		if(!Utils.checkTimeFormat(duration)) {
			plugin.message(sender, "rentout-wrongDuration", duration);
			return;
		}

		rentOut.offer(price, duration);
		region.update();
		plugin.message(sender, "rentout-offering", region, Utils.formatCurrency(price), duration);
	}

	@Override
	public List<String> getTabCompleteList(int toComplete, String[] start, CommandSender sender) {
		List<String> result = new ArrayList<>();
		if(!(sender instanceof Player player) || !sender.hasPermission("areashop.rentout")) {
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
