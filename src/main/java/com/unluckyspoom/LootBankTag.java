package com.unluckyspoom;

import java.util.HashSet;
import java.util.Set;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.banktags.BankTag;

// Tells the Bank Tags plugin which items belong in our filter
public class LootBankTag implements BankTag
{
    private final ItemManager itemManager;
    private volatile Set<Integer> itemIds = new HashSet<>();

    public LootBankTag(ItemManager itemManager)
    {
        this.itemManager = itemManager;
    }

    public void setItemIds(Set<Integer> ids)
    {
        this.itemIds = ids;
    }

    // The bank asks this once per item: "should this one be shown?"
    @Override
    public boolean contains(int itemId)
    {
        return itemIds.contains(itemId) || itemIds.contains(itemManager.canonicalize(itemId));
    }
}