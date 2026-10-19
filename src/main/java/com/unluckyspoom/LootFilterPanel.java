package com.unluckyspoom;

import java.awt.GridLayout;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.PluginPanel;

public class LootFilterPanel extends PluginPanel
{
    private final JTextField searchField = new JTextField();
    private final JPanel listPanel = new JPanel(new GridLayout(0, 1, 0, 2));

    // Which sources are currently ticked
    private final Set<String> selected = new HashSet<>();

    // Source name -> number of different items received from it
    private Map<String, Integer> sources = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public LootFilterPanel(BossBankFilterPlugin plugin)
    {
        setLayout(new DynamicGridLayout(0, 1, 0, 5));

        JButton showButton = new JButton("Show in bank");
        showButton.addActionListener(e -> plugin.applyFilter(selected));

        JButton clearButton = new JButton("Clear filter");
        clearButton.addActionListener(e -> plugin.clearFilter());

        JPanel buttons = new JPanel(new GridLayout(1, 2, 5, 0));
        buttons.add(showButton);
        buttons.add(clearButton);

        add(searchField);
        add(buttons);
        add(listPanel);

        // Re-draw the list every time the search text changes
        searchField.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                rebuild();
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                rebuild();
            }

            @Override
            public void changedUpdate(DocumentEvent e)
            {
                rebuild();
            }
        });
    }

    // Called by the plugin whenever the loot data changes
    public void update(Map<String, Integer> newSources)
    {
        sources = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        sources.putAll(newSources);
        rebuild();
    }

    private void rebuild()
    {
        listPanel.removeAll();
        String filter = searchField.getText().trim().toLowerCase();

        for (Map.Entry<String, Integer> entry : sources.entrySet())
        {
            String name = entry.getKey();
            if (!filter.isEmpty() && !name.toLowerCase().contains(filter))
            {
                continue;
            }

            JCheckBox box = new JCheckBox(name + " (" + entry.getValue() + " items)", selected.contains(name));
            box.addActionListener(e ->
            {
                if (box.isSelected())
                {
                    selected.add(name);
                }
                else
                {
                    selected.remove(name);
                }
            });
            listPanel.add(box);
        }

        listPanel.revalidate();
        listPanel.repaint();
    }
}