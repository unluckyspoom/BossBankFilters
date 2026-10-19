package com.unluckyspoom;

import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
		name = "Boss Bank Filter",
		description = "Remembers loot by source and filters your bank to items from the bosses you pick",
		tags = {"loot", "bank", "filter", "tracker", "boss"},
		internalName = "boss-bank-filter"
)
public class BossBankFilterPlugin extends Plugin
{
	// Saved in .runelite/plugin-data/boss-bank-filter/loot.json
	private static final String SAVE_FILE_NAME = "loot.json";

	// The name our filter is registered under with Bank Tags
	private static final String TAG_NAME = "bossbankfilter";

	// What gets saved: source name -> (item id -> total quantity)
	static class LootData
	{
		Map<String, Map<Integer, Integer>> sources = new HashMap<>();
	}

	@Inject
	private ItemManager itemManager;

	@Inject
	private Gson gson;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientThread clientThread;

	@Inject
	private PluginManager pluginManager;

	// Remembers loot: source name -> (item id -> total quantity)
	private final Map<String, Map<Integer, Integer>> lootBySource = new HashMap<>();

	private LootFilterPanel panel;
	private NavigationButton navButton;
	private LootBankTag lootTag;

	@Override
	protected void startUp()
	{
		loadLoot();

		lootTag = new LootBankTag(itemManager);
		panel = new LootFilterPanel(this);

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/lootfilter.png");

		navButton = NavigationButton.builder()
				.tooltip("Boss Bank Filter")
				.icon(icon)
				.priority(7)
				.panel(panel)
				.build();
		clientToolbar.addNavigation(navButton);

		refreshPanel();
	}

	@Override
	protected void shutDown()
	{
		clearFilter();
		clientToolbar.removeNavigation(navButton);
		saveLoot();
		lootBySource.clear();
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		Map<Integer, Integer> items = lootBySource.computeIfAbsent(event.getName(), k -> new HashMap<>());

		for (ItemStack stack : event.getItems())
		{
			int id = itemManager.canonicalize(stack.getId());
			items.merge(id, stack.getQuantity(), Integer::sum);
		}

		saveLoot();
		refreshPanel();
		log.debug("Recorded loot from {}", event.getName());
	}

	// Called by the "Show in bank" button
	public void applyFilter(Set<String> sourceNames)
	{
		if (sourceNames.isEmpty())
		{
			clearFilter();
			return;
		}

		Set<String> names = new HashSet<>(sourceNames);

		// Bank and loot data are only safe to touch on the client thread
		clientThread.invokeLater(() ->
		{
			Set<Integer> ids = new HashSet<>();
			for (String name : names)
			{
				Map<Integer, Integer> items = lootBySource.get(name);
				if (items != null)
				{
					ids.addAll(items.keySet());
				}
			}
			lootTag.setItemIds(ids);

			BankTagsService bankTags = findBankTags();
			TagManager tagManager = findTagManager();
			if (bankTags == null || tagManager == null)
			{
				log.warn("Bank Tags plugin is not running, can't filter the bank");
				return;
			}

			try
			{
				// Tell Bank Tags our tag exists, then open it by name
				tagManager.registerTag(TAG_NAME, lootTag);
				bankTags.openBankTag(TAG_NAME, 0);
			}
			catch (Exception e)
			{
				log.warn("Could not open bank filter - is your bank open?", e);
			}
		});
	}

	// Called by the "Clear filter" button
	public void clearFilter()
	{
		clientThread.invokeLater(() ->
		{
			BankTagsService bankTags = findBankTags();
			if (bankTags != null && bankTags.getActiveBankTag() == lootTag)
			{
				bankTags.closeBankTag();
			}
		});
	}

	// Bank Tags can't be injected directly, so we look it up in the list of running plugins
	private BankTagsService findBankTags()
	{
		for (Plugin p : pluginManager.getPlugins())
		{
			if (p instanceof BankTagsService && pluginManager.isPluginEnabled(p))
			{
				return (BankTagsService) p;
			}
		}
		return null;
	}

	// Gets the same TagManager that the Bank Tags plugin itself uses
	private TagManager findTagManager()
	{
		for (Plugin p : pluginManager.getPlugins())
		{
			if (p instanceof BankTagsService)
			{
				return p.getInjector().getInstance(TagManager.class);
			}
		}
		return null;
	}

	// Hands the panel a simple summary: source name -> number of different items
	private void refreshPanel()
	{
		Map<String, Integer> snapshot = new HashMap<>();
		for (Map.Entry<String, Map<Integer, Integer>> entry : lootBySource.entrySet())
		{
			snapshot.put(entry.getKey(), entry.getValue().size());
		}
		SwingUtilities.invokeLater(() -> panel.update(snapshot));
	}

	// The file we save to, inside this plugin's own RuneLite data folder
	private Filepath saveFile() throws Exception
	{
		Filepath dir = getPluginDirectory();
		if (!dir.exists())
		{
			dir.createDirectories();
		}
		return dir.joinSegment(SAVE_FILE_NAME);
	}

	private void loadLoot()
	{
		try
		{
			Filepath file = saveFile();
			if (!file.exists())
			{
				return;
			}

			try (var reader = file.openBufferedReader())
			{
				LootData data = gson.fromJson(reader, LootData.class);
				if (data != null && data.sources != null)
				{
					lootBySource.putAll(data.sources);
				}
			}
		}
		catch (Exception e)
		{
			log.warn("Could not load saved loot", e);
		}
	}

	private void saveLoot()
	{
		try
		{
			LootData data = new LootData();
			data.sources.putAll(lootBySource);
			saveFile().write(gson.toJson(data));
		}
		catch (Exception e)
		{
			log.warn("Could not save loot", e);
		}
	}
}