package org.kokakiwi.bukkitupdater.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.kokakiwi.bukkitupdater.BukkitUpdater;

public class ListCommand extends CommandModel {

	@Override
	public boolean execute(CommandSender sender, Command cmd, String commandLabel, String[] args, BukkitUpdater plugin) {
		this.sender = sender;
		this.command = cmd;
		this.commandLabel = commandLabel;
		this.args = args;
		
		if(args.length < 2) {
            sender.sendMessage("Bad syntax for list command. Use /updater list <available/installed> [page]");
            return false;
        }
			
		
		if(sender instanceof Player)
		{
			if(plugin.perms.has((Player) sender, "updater.list"))
			{
				int page;
				
				if(args.length == 3)
					page = Integer.parseInt(args[2]);
				else
					page = 1;
				
				String message = plugin.updater.list(args[1], page);
				
				String[] messages = message.split("\n");
				
				for(String m : messages)
				{
					sender.sendMessage(ChatColor.GRAY.toString() + m);
				}
				
				return true;
			}else {
				sender.sendMessage(ChatColor.RED.toString() + "You're not permitted to use this command!");
				return false;
			}
		}else{
			int page;
			
			if(args.length == 3)
				page = Integer.parseInt(args[2]);
			else
				page = 1;
			
			String message = "Plugins list:\n" + plugin.updater.list(args[1], page);
			
			sender.sendMessage(message);
			return true;
		}
	}

}
