package org.kokakiwi.bukkitupdater.updater;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.kokakiwi.bukkitupdater.BukkitUpdater;
import org.kokakiwi.bukkitupdater.utils.UnZip;
import org.kokakiwi.bukkitupdater.utils.VersionComparator;

public class BUpdater {
	
	private final Logger logger = Logger.getLogger("Minecraft.BukkitUpdater.Updater");
	private final BukkitUpdater plugin;
	private ArrayList<String> repositories;
	private Map<String, BPlugin> plugins;
	private Map<String, String> pluginsName;

	public BUpdater(BukkitUpdater bukkitUpdater) {
		this.plugin = bukkitUpdater;
		
		if(!new File("bukkitUpdates/plugins/").exists())
			new File("bukkitUpdates/plugins/").mkdirs();
		
		if(!new File("bukkitUpdates/archives/").exists())
			new File("bukkitUpdates/archives/").mkdirs();
	}
	
	public void downloadLists()
	{
		repositories = new ArrayList<String>();
		for(String url : plugin.getConfig().getUpdateUrls())
		{
			plugin.download.download(url, new File(plugin.getDataFolder(), "cache/" + url.substring(url.lastIndexOf("/") + 1)));
			repositories.add(url.substring(url.lastIndexOf("/") + 1));
		}
	}
	
	public String getBuildNumber(String bukkitUrl) throws IOException {
	    URL url = new URL(bukkitUrl);
	    URLConnection urlConnection = url.openConnection();
	    HttpURLConnection connection = null;
	    connection = (HttpURLConnection) urlConnection;
	    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
	    String current;
	    while ((current = in.readLine()) != null) {
	    	if(current.contains("Build")) {
	    	    String txt = current;
	    	    String re1=".*?";
	    	    String re2="(#)";
	    	    String re3="(\\d+)";
	    	    
	    	    Pattern p = Pattern.compile(re1+re2+re3,Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	    	    Matcher m = p.matcher(txt);
	    	    if (m.find())
	    	    {
	    	        String int1=m.group(2);
	    	        logger.info("Found : " + int1);
	    	        return int1;
	    	    }
	    		break;
	    	}
	    }
	    return null;
	}
	
	@SuppressWarnings("unchecked")
	public boolean updateLists() {
		plugins = new HashMap<String, BPlugin>();
		pluginsName = new HashMap<String, String>();
		
		for(String repository : repositories)
		{
			SAXBuilder sxb = new SAXBuilder();
			try {
				Document document = sxb.build(new File(plugin.getDataFolder(), "cache/" + repository));
				Element rootNode = document.getRootElement();
				
				List<Element> repoPlugins = rootNode.getChild("plugins").getChildren("plugin");
				Iterator<Element> i = repoPlugins.iterator();
				while(i.hasNext())
				{
					Element plug = i.next();
					
					BPlugin bplug = new BPlugin(plug.getChildText("id"), plug.getChildText("name"), plug.getChildText("version"),
							plug.getChildText("author"), plug.getChildText("file-type"), plug.getChildText("file-url"));
					
					if(plug.getChildText("url") != null)
						bplug.url = plug.getChildText("url");
					
					if(bplug.fileType.equalsIgnoreCase("archive"))
						bplug.archive = plug.getChild("archive");
					
					if(plug.getChildText("dependencies") != null)
						bplug.dependencies = plug.getChild("dependencies").getChildren("dep");
					
					if(plug.getChildText("bukkit-build") != null)
						bplug.bukkitBuild = plug.getChildText("bukkit-build");
					
					plugins.put(plug.getChildText("id"), bplug);
					pluginsName.put(bplug.name, bplug.id);
				}
				return true;
			} catch (JDOMException e) {
				logger.warning("BukkitUpdater : Error during parsing repository '" + repository + "'");
				e.printStackTrace();
				return false;
			} catch (IOException e) {
				logger.warning("BukkitUpdater : Error during parsing repository '" + repository + "'");
				e.printStackTrace();
				return false;
			}
		}
		return false;
	}
	
	public ArrayList<BPlugin> check()
	{
		logger.info("BukkitUpdater : Checking updates from repository in cache...");
		ArrayList<BPlugin> checker = new ArrayList<BPlugin>();
		Plugin[] loadedPlugins = plugin.getPluginManager().getPlugins();
		for(Plugin plug : loadedPlugins)
		{
			BPlugin bplug;
			if(plugins.get(plug.getDescription().getName()) != null)
				bplug = plugins.get(plug.getDescription().getName());
			else if(plugins.get(pluginsName.get(plug.getDescription().getName())) != null)
				bplug = plugins.get(pluginsName.get(plug.getDescription().getName()));
			else {
				logger.info("BukkitUpdater : Plugin '" + plug.getDescription().getName() + "' isn't in loaded repositories");
				continue;
			}
			
			String currentVersion = plug.getDescription().getVersion();
			String newVersion = bplug.version;
			String compared = VersionComparator.compare(currentVersion, newVersion);
			if(compared.equals("<"))
			{
				checker.add(bplug);
				logger.info("BukkitUpdater : New version detected for '" + plug.getDescription().getName() + "' (new version: " + newVersion + ")");
			}
		}
		return checker;
	}
	
	public boolean update()
	{
		logger.info("BukkitUpdater : Updating...");
		downloadLists();
		updateLists();
		
		//Update CraftBukkit
		String coreVersion = plugin.config.getConfig().getString("bukkitCoreVersion");
		String currentVersion = getCurrentVersion();
		String url = null;
		String coreUrl = null;
		if(coreVersion.equalsIgnoreCase("recommended"))
		{
			url = "http://jenkins.lukegb.com/job/dev-CraftBukkit/Recommended/";
			coreUrl = "http://jenkins.lukegb.com/job/dev-CraftBukkit/Recommended/artifact/target/craftbukkit-0.0.1-SNAPSHOT.jar";
		}else if(coreVersion.equalsIgnoreCase("snapshot") || coreVersion.equalsIgnoreCase("stable") || coreVersion.equalsIgnoreCase("latest")) {
			url = "http://jenkins.lukegb.com/job/dev-CraftBukkit/lastStableBuild/";
			coreUrl = "http://jenkins.lukegb.com/job/dev-CraftBukkit/lastStableBuild/artifact/target/craftbukkit-0.0.1-SNAPSHOT.jar";
		}
		try {
			String newVersion = getBuildNumber(url);
			if(newVersion != null)
			{
				if(VersionComparator.compare(newVersion, currentVersion).equals(">"))
				{
					plugin.download.download(coreUrl, new File("bukkitUpdater/craftbukkit-0.0.1-SNAPSHOT.jar"));
					logger.info("BukkitUpdater : New CraftBukkit build available (#" + newVersion + "), this server using build #" + currentVersion + " !");
					logger.info("				 You should update the server. New version downloaded in bukkitUpdater folder.");
				}
			}else
				logger.warning("BukkitUpdater : Error during getting last Bukkit build number.");
		} catch (IOException e) {
			logger.warning("BukkitUpdater : Error during getting last Bukkit build number.");
			e.printStackTrace();
		}
		
		
		//Update plugins
		ArrayList<BPlugin> newPlugins = check();
		for(BPlugin plug : newPlugins)
		{
			if(plug.bukkitBuild != null)
			{
				if(VersionComparator.compare(plug.bukkitBuild, plugin.getServer().getVersion()).equals("<"))
				{
					logger.warning("BukkitUpdater : Plugin '" + plug.name + "' require CraftBukkit build " + plug.bukkitBuild + " and you have build " + plugin.getServer().getVersion());
					logger.warning("                You should update your server core.");
				}
			}
			
			if(plug.dependencies != null)
			{
				Iterator i = plug.dependencies.iterator();
				while(i.hasNext())
				{
					Element dep = (Element) i.next();
					if(plugins.get(dep.getAttributeValue("id")) != null)
					{
						BPlugin depend = plugins.get(dep.getAttributeValue("id"));
						if(plugin.getPluginManager().getPlugin(depend.name) == null)
						{
							String installDep = install(depend.id);
						}
					}else {
						logger.warning("BukkitUpdater : Plugin require depency '" + dep.getAttributeValue("id") + "' but it isn't in repositories.");
						logger.warning("				You should download it manually.");
					}
				}
			}
			
			if(plug.fileType.equalsIgnoreCase("jar"))
				plugin.download.download(plug.fileUrl, new File("bukkitUpdates/plugins/" + plug.fileUrl.substring(plug.fileUrl.lastIndexOf("/") + 1)));
			else if(plug.fileType.equalsIgnoreCase("archive"))
			{
				plugin.download.download(plug.fileUrl, new File("bukkitUpdates/archives/" + plug.fileUrl.substring(plug.fileUrl.lastIndexOf("/") + 1)));
				UnZip.unzip(new File("bukkitUpdates/archives/" + plug.fileUrl.substring(plug.fileUrl.lastIndexOf("/") + 1)), new File("bukkitUpdates/plugins/"));
			}
		}
		
		return true;
	}
	
	private String getCurrentVersion() {
		String[] versionSplitted = plugin.getServer().getVersion().split("-");
		String currentVersion = versionSplitted[3];
		//git-Bukkit-0.0.0-493-g8b5496e-b493jnks
		return currentVersion;
	}

	public String install(String id)
	{
		if(plugins.get(id) != null)
		{
			BPlugin plug = plugins.get(id);
			if(plugin.getPluginManager().getPlugin(plug.name) == null)
			{
				if(plug.bukkitBuild != null)
				{
					if(!VersionComparator.compare(plug.bukkitBuild, getCurrentVersion()).equals("=="))
					{
						logger.warning("BukkitUpdater : Plugin '" + plug.name + "' require CraftBukkit build " + plug.bukkitBuild + " and you have build " + plugin.getServer().getVersion());
						logger.warning("                You should update your server core.");
					}
				}
				
				if(plug.dependencies != null)
				{
					Iterator i = plug.dependencies.iterator();
					while(i.hasNext())
					{
						Element dep = (Element) i.next();
						if(plugins.get(dep.getAttributeValue("id")) != null)
						{
							BPlugin depend = plugins.get(dep.getAttributeValue("id"));
							if(plugin.getPluginManager().getPlugin(depend.name) == null)
							{
								String installDep = install(depend.id);
							}
						}else {
							logger.warning("BukkitUpdater : Plugin require depency '" + dep.getAttributeValue("id") + "' but it isn't in repositories.");
							logger.warning("				You should download it manually.");
						}
					}
				}
				
				logger.info("BukkitUpdater : Installing '" + plug.name + "' ...");
				if(plug.fileType.equalsIgnoreCase("jar"))
					plugin.download.download(plug.fileUrl, new File("plugins/" + plug.fileUrl.substring(plug.fileUrl.lastIndexOf("/") + 1)));
				else if(plug.fileType.equalsIgnoreCase("archive"))
				{
					plugin.download.download(plug.fileUrl, new File("bukkitUpdates/archives/" + plug.fileUrl.substring(plug.fileUrl.lastIndexOf("/") + 1)));
					UnZip.unzip(new File("bukkitUpdates/archives/" + plug.fileUrl.substring(plug.fileUrl.lastIndexOf("/") + 1)), new File("plugins/"));
				}
				
				String loadPlugin = load(id);
				return "Plugin installed!";
			}else{
				return "Plugin already installed!";
			}
		}else{
			return "Plugin didn't exists!";
		}
	}
	
	public String remove(String id)
	{
		if(plugins.get(id) != null)
		{
			BPlugin plug = plugins.get(id);
			if(plugin.getPluginManager().getPlugin(plug.name) != null)
			{
				if(plugin.getPluginManager().isPluginEnabled(plug.name))
				{
					plugin.getPluginManager().disablePlugin(plugin.getPluginManager().getPlugin(plug.name));
				}
				String jarName = plug.fileUrl.substring(plug.fileUrl.lastIndexOf("/") + 1);
				new File("plugins/", jarName).delete();
				return "Plugin removed!";
			}else{
				return "Plugin isn't loaded!";
			}
		}else{
			return "Plugin didn't exists!";
		}
	}
	
	public String purge(String id)
	{
		if(plugins.get(id) != null)
		{
			BPlugin plug = plugins.get(id);
			if(plugin.getPluginManager().getPlugin(plug.name) != null)
			{
				if(plugin.getPluginManager().isPluginEnabled(plug.name))
				{
					plugin.getPluginManager().disablePlugin(plugin.getPluginManager().getPlugin(plug.name));
				}
				String jarName = plug.fileUrl.substring(plug.fileUrl.lastIndexOf("/") + 1);
				plugin.getPluginManager().getPlugin(plug.name).getDataFolder().delete();
				new File("plugins/", jarName).delete();
				return "Plugin purged!";
			}else{
				return "Plugin isn't installed!";
			}
		}else{
			return "Plugin didn't exists!";
		}
	}
	
	public String enable(String arg)
	{
		if(plugins.get(arg) != null)
		{
			plugin.getPluginManager().enablePlugin(plugin.getPluginManager().getPlugin(plugins.get(arg).name));
			return "Plugin enabled.";
		}else if(plugin.getPluginManager().getPlugin(arg) != null)
		{
			plugin.getPluginManager().enablePlugin(plugin.getPluginManager().getPlugin(arg));
			return "Plugin enabled.";
		}else{
			return "Plugin didn't exists!";
		}
	}
	
	public String disable(String arg)
	{
		if(plugins.get(arg) != null)
		{
			plugin.getPluginManager().disablePlugin(plugin.getPluginManager().getPlugin(plugins.get(arg).name));
			return "Plugin disabled.";
		}else if(plugin.getPluginManager().getPlugin(arg) != null)
		{
			plugin.getPluginManager().disablePlugin(plugin.getPluginManager().getPlugin(arg));
			return "Plugin disabled.";
		}else{
			return "Plugin didn't exists!";
		}
	}
	
	public String load(String arg)
	{
		if(plugins.get(arg) != null)
		{
			File jarFile = new File("plugins/", plugins.get(arg).fileUrl.substring(plugins.get(arg).fileUrl.lastIndexOf("/") + 1));
			return "Plugin disabled.";
		}else {
			if(new File("plugins/", arg + ".jar").exists())
			{
				try {
					plugin.getPluginManager().loadPlugin(new File("plugins/", arg + ".jar"));
					return "Plugin loaded.";
				} catch (Exception e) {
					logger.severe("Error during loading plugin '" + arg + "' !");
					e.printStackTrace();
					return "Error during loading plugin.";
				}
			}else {
				return "Plugin not found.";
			}
		}
	}
}