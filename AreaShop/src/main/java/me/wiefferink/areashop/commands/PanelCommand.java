package me.wiefferink.areashop.commands;

import me.wiefferink.areashop.gui.ShopGui;
import me.wiefferink.areashop.gui.ShopsGui;
import me.wiefferink.areashop.regions.GeneralRegion;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Open the menu where a player manages the shops they rent or own.
 */
public class PanelCommand extends CommandAreaShop {

	@Override
	public String getCommandStart() {
		return "areashop panel";
	}

	@Override
	public String getHelp(CommandSender target) {
		if(target.hasPermission("areashop.panel")) {
			return "help-panel";
		}
		return null;
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		if(!sender.hasPermission("areashop.panel")) {
			plugin.message(sender, "panel-noPermission");
			return;
		}
		if(!(sender instanceof Player player)) {
			plugin.message(sender, "cmd-onlyByPlayer");
			return;
		}

		// Without arguments the player gets the list of everything they have
		if(args.length < 2) {
			new ShopsGui(player).open();
			return;
		}

		GeneralRegion region = plugin.getFileManager().getRegion(args[1]);
		if(region == null) {
			plugin.message(sender, "panel-notRegistered", args[1]);
			return;
		}
		if(!region.isOwner(player)) {
			plugin.message(sender, "panel-notYours", region);
			return;
		}
		new ShopGui(player, region, new ShopsGui(player)).open();
	}

	@Override
	public List<String> getTabCompleteList(int toComplete, String[] start, CommandSender sender) {
		List<String> result = new ArrayList<>();
		if(toComplete == 2 && sender instanceof Player player) {
			for(GeneralRegion region : plugin.getFileManager().getRegions()) {
				if(region.isOwner(player)) {
					result.add(region.getName());
				}
			}
		}
		return result;
	}

}
