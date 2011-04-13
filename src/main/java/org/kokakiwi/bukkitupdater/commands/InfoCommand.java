package org.kokakiwi.bukkitupdater.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.kokakiwi.bukkitupdater.BukkitUpdater;
import org.kokakiwi.bukkitupdater.updater.BPlugin;

public class InfoCommand extends CommandModel {

	@Override
	public boolean execute(CommandSender sender, Command cmd, String commandLabel, String[] args, BukkitUpdater plugin) {
		if(args.length != 2)
		{
			sender.sendMessage(ChatColor.RED + "Bad syntax for list command. Use /updater info <plugin ID>");
			return false;
		}
		
		if(sender instanceof Player)
		{
			if(!plugin.perms.has((Player) sender, "updater.info"))
			{
				sender.sendMessage(ChatColor.RED.toString() + "You're not permitted to use this command!");
				return false;
			}
		}
		
		BPlugin plug = plugin.updater.getPlugin(args[1]);
		
		sender.sendMessage(ChatColor.GREEN + "Name" + ChatColor.WHITE + " : " + ChatColor.LIGHT_PURPLE 
							+ plug.name + ChatColor.AQUA + " v" + plug.version);
		sender.sendMessage(ChatColor.GREEN + "Author" + ChatColor.WHITE + " : " + ChatColor.AQUA
							+ plug.author + (plug.website != null ? ChatColor.GRAY + " (" + plug.website + ")" : ""));
		if(plug.description != null)
			sender.sendMessage(ChatColor.GREEN + "Description" + ChatColor.WHITE 
								+ " : " + ChatColor.DARK_GRAY + plug.description);
		
		
		return true;
	}

}
