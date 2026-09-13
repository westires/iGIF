// /igif menu komutuyla açılan chest GUI. Yüklü animasyonları gösterir, tıkla yönet.
package com.westires.igif.gui;

import com.westires.igif.animation.Animation;
import com.westires.igif.animation.AnimationLoader;
import com.westires.igif.gif.AnimationProcessor;
import com.westires.igif.playback.PlaybackManager;
import com.westires.igif.util.ConsoleLogger;
import com.westires.igif.util.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AnimationMenuGui implements Listener {

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;

    private final Plugin plugin;
    private final AnimationLoader loader;
    private final AnimationProcessor processor;
    private final PlaybackManager playback;
    private final ConsoleLogger log;
    private final MessageService messages;

    // player → their open menu inventory
    private final Map<UUID, Inventory> openMenus = new ConcurrentHashMap<>();
    // slot → animation id within a player's open inventory
    private final Map<UUID, Map<Integer, String>> slotMaps = new ConcurrentHashMap<>();

    public AnimationMenuGui(Plugin plugin, AnimationLoader loader, AnimationProcessor processor,
                            PlaybackManager playback, ConsoleLogger log, MessageService messages) {
        this.plugin    = plugin;
        this.loader    = loader;
        this.processor = processor;
        this.playback  = playback;
        this.log       = log;
        this.messages  = messages;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, SIZE,
                Component.text("iGIF — Animation Manager", NamedTextColor.DARK_GREEN));

        Map<Integer, String> slotMap = new HashMap<>();
        List<Animation> anims = new ArrayList<>(loader.getAll());
        anims.sort(Comparator.comparing(Animation::getId));

        for (int i = 0; i < anims.size() && i < SIZE - 9; i++) {
            Animation anim = anims.get(i);
            ItemStack item = buildAnimItem(anim);
            inv.setItem(i, item);
            slotMap.put(i, anim.getId());
        }

        // Bottom row: info / filler
        ItemStack info = named(Material.BOOK, "§a§liGIF Menu",
                List.of("§7Left-click: §aPlay preview",
                        "§7Right-click: §cStop animation",
                        "§7Shift+Left: §eRegenerate",
                        "§7Shift+Right: §4Delete"));
        for (int s = SIZE - 9; s < SIZE; s++) inv.setItem(s, border());
        inv.setItem(SIZE - 5, info);

        openMenus.put(player.getUniqueId(), inv);
        slotMaps.put(player.getUniqueId(), slotMap);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory open = openMenus.get(player.getUniqueId());
        if (open == null || !event.getInventory().equals(open)) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        Map<Integer, String> slotMap = slotMaps.get(player.getUniqueId());
        if (slotMap == null) return;
        String animId = slotMap.get(slot);
        if (animId == null) return;

        boolean shift = event.isShiftClick();
        boolean right = event.isRightClick();

        if (shift && right) {
            // Delete
            player.closeInventory();
            player.performCommand("igif delete " + animId);
        } else if (shift) {
            // Regenerate
            player.closeInventory();
            player.performCommand("igif regenerate " + animId);
        } else if (right) {
            // Stop
            playback.stopAll(player);
            messages.send(player, "animation-stopped-all", MessageService.of("player", player.getName()));
        } else {
            // Play preview for the opener
            loader.get(animId).ifPresent(anim -> {
                if (!anim.isGenerated()) {
                    messages.send(player, "animation-not-generated", MessageService.of("animation", animId));
                    return;
                }
                playback.play(player, anim);
                messages.send(player, "animation-playing", MessageService.of(
                        "animation", animId, "player", player.getName(), "type",
                        anim.getConfig().displayType().name()));
            });
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID uid = event.getPlayer().getUniqueId();
        openMenus.remove(uid);
        slotMaps.remove(uid);
    }

    // ─── Item builders ───────────────────────────────────────────────────────

    private ItemStack buildAnimItem(Animation anim) {
        Material mat = anim.isGenerated() ? Material.FILLED_MAP : Material.MAP;
        String status = anim.isGenerated()
                ? "§a✔ Generated (" + anim.getFrameCount() + " frames)"
                : "§c✘ Not generated";

        return named(mat, "§b§l" + anim.getId(), List.of(
                status,
                "§7FPS: §f" + anim.getConfig().fps(),
                "§7Type: §f" + anim.getConfig().displayType().name(),
                "§7Size: §f" + anim.getConfig().size() + "px",
                "",
                "§aLeft: §fPlay  §cRight: §fStop",
                "§eShift+Left: §fRegenerate  §4Shift+Right: §fDelete"
        ));
    }

    private static ItemStack named(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        List<Component> loreComp = new ArrayList<>();
        for (String l : lore) loreComp.add(Component.text(l).decoration(TextDecoration.ITALIC, false));
        meta.lore(loreComp);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack border() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.displayName(Component.text(" ")); item.setItemMeta(meta); }
        return item;
    }
}