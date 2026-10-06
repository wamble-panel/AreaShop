package me.wiefferink.areashop.commands;

import me.wiefferink.areashop.gui.GuideGui;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Open the menu that explains how everything works.
 */
public class GuideCommand extends CommandAreaShop {

	@Override
	public String getCommandStart() {
		return "areashop guide";
	}

	@Override
	public String getHelp(CommandSender target) {
		if(target.hasPermission("areashop.guide")) {
			return "help-guide";
		}
		return null;
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		if(!sender.hasPermission("areashop.guide")) {
			plugin.message(sender, "guide-noPermission");
			return;
		}
		if(!(sender instanceof Player player)) {
			plugin.message(sender, "cmd-onlyByPlayer");
			return;
		}
		new GuideGui(player, null).open();
	}

}
