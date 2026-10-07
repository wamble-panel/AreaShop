package me.wiefferink.areashop.commands;

import me.wiefferink.areashop.gui.RentFromGui;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Open the list of the shops other players are renting out.
 */
public class RentFromCommand extends CommandAreaShop {

	@Override
	public String getCommandStart() {
		return "areashop rentfrom";
	}

	@Override
	public String getHelp(CommandSender target) {
		if(target.hasPermission("areashop.rentfrom")) {
			return "help-rentfrom";
		}
		return null;
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		if(!sender.hasPermission("areashop.rentfrom")) {
			plugin.message(sender, "rentout-noPermissionRent");
			return;
		}
		if(!(sender instanceof Player player)) {
			plugin.message(sender, "cmd-onlyByPlayer");
			return;
		}
		new RentFromGui(player).open();
	}

}
