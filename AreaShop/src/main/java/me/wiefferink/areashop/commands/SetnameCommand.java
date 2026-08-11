package me.wiefferink.areashop.commands;

import me.wiefferink.areashop.messages.Colors;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.tools.Utils;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Set the name a region is shown under, for servers running a marketplace.
 *
 * <p>Only an administrator can change this, the player renting or buying the region cannot.
 */
public class SetnameCommand extends CommandAreaShop {

	/** Words that clear the display name instead of setting one. */
	private static final List<String> RESET_WORDS = List.of("reset", "default", "-");

	/** Used when the config does not say how long a display name may be. */
	private static final int DEFAULT_MAXIMUM_LENGTH = 64;

	@Override
	public String getCommandStart() {
		return "areashop setname";
	}

	@Override
	public String getHelp(CommandSender target) {
		if(target.hasPermission("areashop.setname")) {
			return "help-setname";
		}
		return null;
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		if(!sender.hasPermission("areashop.setname")) {
			plugin.message(sender, "setname-noPermission");
			return;
		}
		if(args.length < 3) {
			plugin.message(sender, "setname-help");
			return;
		}

		GeneralRegion region = plugin.getFileManager().getRegion(args[1]);
		if(region == null) {
			plugin.message(sender, "setname-notRegistered", args[1]);
			return;
		}

		// Everything after the region name is the display name, so it can contain spaces
		String name = Utils.join(args, " ", 2, args.length).trim();

		if(RESET_WORDS.contains(name.toLowerCase())) {
			region.setDisplayName(null);
			region.update();
			plugin.message(sender, "setname-reset", region);
			return;
		}

		// Color codes do not take up space on screen, so judge the length by what is readable
		int maximumLength = plugin.getConfig().getInt("shopNameMaxLength", DEFAULT_MAXIMUM_LENGTH);
		String visible = Colors.strip(name);
		if(visible.length() > maximumLength) {
			plugin.message(sender, "setname-tooLong", maximumLength, visible.length());
			return;
		}

		region.setDisplayName(name);
		region.update();
		plugin.message(sender, "setname-success", region);
	}

	@Override
	public List<String> getTabCompleteList(int toComplete, String[] start, CommandSender sender) {
		List<String> result = new ArrayList<>();
		if(!sender.hasPermission("areashop.setname")) {
			return result;
		}
		if(toComplete == 2) {
			result.addAll(plugin.getFileManager().getRegionNames());
		} else if(toComplete == 3) {
			// Offer the current name, so it can be tab completed and then edited
			GeneralRegion region = plugin.getFileManager().getRegion(start[2]);
			if(region != null && region.hasDisplayName()) {
				result.add(region.getDisplayName());
			}
			result.addAll(RESET_WORDS);
		}
		return result;
	}

}
