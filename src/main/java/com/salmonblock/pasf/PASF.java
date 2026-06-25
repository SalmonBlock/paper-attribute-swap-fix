package com.salmonblock.pasf;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;

public class PASF extends JavaPlugin {

    private boolean toggled;

    // Paper config reflection
    private Field equipmentUpdateField;
    private Object equipmentUpdateHolder;
    private boolean originalConfigValue = true;
    private boolean configFound = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        toggled = getConfig().getBoolean("enabled", true);

        configFound = findPaperConfig();
        if (toggled) applyState();

        getServer().getPluginManager().registerEvents(new SwapListener(this), this);

        getLogger().info("PASF " + (configFound ? "(Paper config found)" : "(fallback mode)")
                + " - Vanilla swap: " + (toggled ? "ON" : "OFF"));
    }

    @Override
    public void onDisable() {
        if (configFound) {
            setPaperEquipmentUpdate(originalConfigValue);
            getLogger().info("Restored Paper equipment update to: " + originalConfigValue);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("pasf")) return false;

        toggled = !toggled;
        applyState();
        getConfig().set("enabled", toggled);
        saveConfig();
        sender.sendMessage("\u00a76[PASF] \u00a7fVanilla attribute swap: "
                + (toggled ? "\u00a7aON" : "\u00a7cOFF"));
        return true;
    }

    public boolean isToggled() {
        return toggled;
    }

    public boolean isConfigFound() {
        return configFound;
    }

    private void applyState() {
        if (configFound) {
            // toggled ON = disable Paper's fix (false), toggled OFF = enable it (true)
            setPaperEquipmentUpdate(!toggled);
        }
    }

    private boolean findPaperConfig() {
        try {
            Class<?> gc = Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
            Object config = gc.getMethod("get").invoke(null);

            for (Field section : gc.getDeclaredFields()) {
                section.setAccessible(true);
                Object sectionObj = section.get(config);
                if (sectionObj == null) continue;

                for (Field f : sectionObj.getClass().getDeclaredFields()) {
                    String name = f.getName().toLowerCase();
                    if (name.contains("equipment") && f.getType() == boolean.class) {
                        f.setAccessible(true);
                        equipmentUpdateField = f;
                        equipmentUpdateHolder = sectionObj;
                        originalConfigValue = f.getBoolean(sectionObj);
                        getLogger().info("Found: " + section.getName() + "." + f.getName()
                                + " = " + originalConfigValue);
                        return true;
                    }
                }
            }
            getLogger().warning("Paper GlobalConfiguration found but equipment field missing");
        } catch (ClassNotFoundException e) {
            getLogger().warning("Not running on Paper — GlobalConfiguration not found");
        } catch (Exception e) {
            getLogger().warning("Error reading Paper config: " + e.getMessage());
        }
        return false;
    }

    private void setPaperEquipmentUpdate(boolean value) {
        try {
            equipmentUpdateField.setBoolean(equipmentUpdateHolder, value);
        } catch (Exception e) {
            getLogger().warning("Failed to set equipment update: " + e.getMessage());
        }
    }
}
