package com.salmonblock.pasf;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SwapListener implements Listener {

    private static final int SWAP_WINDOW_TICKS = 4;

    private final PASF plugin;
    private final NamespacedKey dmgKey;
    private final NamespacedKey spdKey;

    // Snapshot of every player's attackStrengthTicker, updated every tick
    // so we always have the value from BEFORE Paper resets it
    private final Map<UUID, Integer> tickerSnapshots = new ConcurrentHashMap<>();

    // NMS reflection
    private Method getHandleMethod;
    private Field attackTickerField;
    private boolean tickerAvailable = true;

    public SwapListener(PASF plugin) {
        this.plugin = plugin;
        this.dmgKey = new NamespacedKey(plugin, "pasf-dmg");
        this.spdKey = new NamespacedKey(plugin, "pasf-spd");

        // Snapshot every player's attack ticker every tick
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.isToggled()) return;
            for (Player p : Bukkit.getOnlinePlayers()) {
                int ticker = getAttackTicker(p);
                if (ticker >= 0) {
                    tickerSnapshots.put(p.getUniqueId(), ticker);
                }
            }
        }, 1L, 1L);
    }

    // --- Item swap listeners ---

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHotbarSwap(PlayerItemHeldEvent event) {
        if (!plugin.isToggled()) return;
        handleSwap(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHandSwap(PlayerSwapHandItemsEvent event) {
        if (!plugin.isToggled()) return;
        handleSwap(event.getPlayer());
    }

    private void handleSwap(Player player) {
        UUID uuid = player.getUniqueId();

        // Use the snapshot from the PREVIOUS tick — guaranteed to be pre-reset
        Integer savedTicker = tickerSnapshots.get(uuid);

        // Also capture current values for attribute fallback
        double oldDamage = attrValue(player, Attribute.ATTACK_DAMAGE);
        double oldSpeed = attrValue(player, Attribute.ATTACK_SPEED);

        // Restore ticker IMMEDIATELY (handles same-tick attacks)
        if (savedTicker != null && savedTicker >= 0) {
            setAttackTicker(player, savedTicker);
        }

        // Also restore on next tick (backup — Paper may reset it after this handler)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !plugin.isToggled()) return;

            if (savedTicker != null && savedTicker >= 0) {
                setAttackTicker(player, savedTicker);
            }

            // Attribute modifier fallback when config toggle is unavailable
            if (!plugin.isConfigFound()) {
                double newDamage = attrValue(player, Attribute.ATTACK_DAMAGE);
                double newSpeed = attrValue(player, Attribute.ATTACK_SPEED);

                cleanupModifiers(player);
                applyTempModifier(player, Attribute.ATTACK_DAMAGE, dmgKey, oldDamage - newDamage);
                applyTempModifier(player, Attribute.ATTACK_SPEED, spdKey, oldSpeed - newSpeed);

                Bukkit.getScheduler().runTaskLater(plugin, () -> cleanupModifiers(player),
                        SWAP_WINDOW_TICKS);
            }
        });
    }

    // --- Cleanup ---

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        tickerSnapshots.remove(uuid);
        cleanupModifiers(event.getPlayer());
    }

    // --- Attribute helpers ---

    private double attrValue(Player player, Attribute attribute) {
        AttributeInstance inst = player.getAttribute(attribute);
        return inst != null ? inst.getValue() : 0;
    }

    private void applyTempModifier(Player player, Attribute attribute,
                                   NamespacedKey key, double amount) {
        if (amount == 0) return;
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) return;

        AttributeModifier mod = new AttributeModifier(
                key, amount, AttributeModifier.Operation.ADD_NUMBER);
        try {
            inst.addTransientModifier(mod);
        } catch (NoSuchMethodError e) {
            try { inst.addModifier(mod); } catch (Exception ignored) {}
        }
    }

    private void cleanupModifiers(Player player) {
        removeMod(player, Attribute.ATTACK_DAMAGE, dmgKey);
        removeMod(player, Attribute.ATTACK_SPEED, spdKey);
    }

    private void removeMod(Player player, Attribute attribute, NamespacedKey key) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) return;
        try { inst.removeModifier(key); } catch (Exception ignored) {}
    }

    // --- NMS reflection for cooldown ticker ---

    private void ensureReflection(Player player) {
        if (getHandleMethod != null || !tickerAvailable) return;
        try {
            getHandleMethod = player.getClass().getMethod("getHandle");
            Object handle = getHandleMethod.invoke(player);

            Class<?> clazz = handle.getClass();
            while (clazz != null && attackTickerField == null) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (f.getName().equals("attackStrengthTicker") && f.getType() == int.class) {
                        f.setAccessible(true);
                        attackTickerField = f;
                        plugin.getLogger().info("Found NMS attackStrengthTicker");
                        return;
                    }
                }
                clazz = clazz.getSuperclass();
            }
            plugin.getLogger().warning("attackStrengthTicker not found — cooldown restore disabled");
            tickerAvailable = false;
        } catch (Exception e) {
            plugin.getLogger().warning("NMS reflection failed: " + e.getMessage());
            tickerAvailable = false;
        }
    }

    private int getAttackTicker(Player player) {
        ensureReflection(player);
        if (!tickerAvailable) return -1;
        try {
            return attackTickerField.getInt(getHandleMethod.invoke(player));
        } catch (Exception e) { return -1; }
    }

    private void setAttackTicker(Player player, int value) {
        if (!tickerAvailable) return;
        try {
            attackTickerField.setInt(getHandleMethod.invoke(player), value);
        } catch (Exception ignored) {}
    }
}
