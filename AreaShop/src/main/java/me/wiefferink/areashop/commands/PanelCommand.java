package me.wiefferink.areashop.commands;

import me.wiefferink.areashop.gui.ShopGui;
import me.wiefferink.areashop.gui.ShopsGui;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.tools.Utils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Open the menu where a player manages the shops they rent or own.
 *
 * <p>With the name of one of their shops it opens straight into that shop. Staff with
 * {@code areashop.panel.others} can also name a region they do not hold, or a player, to look at
 * what someone else has.
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
		if(region != null) {
			if(!region.isOwner(player) && !player.hasPermission("areashop.panel.others")) {
				plugin.message(sender, "panel-notYours", region);
				return;
			}
			new ShopGui(player, region, new ShopsGui(player)).open();
			return;
		}

		// Not a region, so it is read as the player whose shops to look at
		if(!player.hasPermission("areashop.panel.others")) {
			plugin.message(sender, "panel-notRegistered", args[1]);
			return;
		}

		OfflinePlayer subject = Utils.findOfflinePlayer(args[1]);
		if(subject == null) {
			plugin.message(sender, "panel-noPlayer", args[1]);
			return;
		}
		new ShopsGui(player, subject).open();
	}

	@Override
	public List<String> getTabCompleteList(int toComplete, String[] start, CommandSender sender) {
		List<String> result = new ArrayList<>();
		if(toComplete != 2) {
			return result;
		}
		if(sender instanceof Player player) {
			for(GeneralRegion region : plugin.getFileManager().getRegions()) {
				if(region.isOwner(player)) {
					result.add(region.getName());
				}
			}
		}
		if(sender.hasPermission("areashop.panel.others")) {
			for(Player online : Bukkit.getOnlinePlayers()) {
				result.add(online.getName());
			}
		}
		return result;
	}

}
