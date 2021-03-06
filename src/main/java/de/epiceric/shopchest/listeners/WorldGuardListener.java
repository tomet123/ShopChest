package de.epiceric.shopchest.listeners;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.nms.Hologram;
import de.epiceric.shopchest.shop.Shop;
import de.epiceric.shopchest.utils.ClickType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.codemc.worldguardwrapper.WorldGuardWrapper;
import org.codemc.worldguardwrapper.event.DamageEntityEvent;
import org.codemc.worldguardwrapper.event.UseBlockEvent;
import org.codemc.worldguardwrapper.event.UseEntityEvent;

public class WorldGuardListener implements Listener {

    private ShopChest plugin;

    public WorldGuardListener(ShopChest plugin) {
        this.plugin = plugin;
    }


    private boolean isAllowed(Player player, Location location, Action action) {
        Shop shop = plugin.getShopUtils().getShop(location);

        if (action == Action.RIGHT_CLICK_BLOCK && shop != null) {
            if (shop.getVendor().getUniqueId().equals(player.getUniqueId()) && shop.getShopType() != Shop.ShopType.ADMIN) {
                return true;
            }
        }

        if (ClickType.getPlayerClickType(player) != null) {
            switch (ClickType.getPlayerClickType(player).getClickType()) {
                case CREATE:
                    return WorldGuardWrapper.getInstance().queryStateFlag(player, location, "create-shop").orElse(false);
                case REMOVE:
                case INFO:
                case OPEN:
                    return true;
            }
        } else {
            if (shop != null) {
                String flagName = (shop.getShopType() == Shop.ShopType.NORMAL ? "use-shop" : "use-admin-shop");
                return WorldGuardWrapper.getInstance().queryStateFlag(player, location, flagName).orElse(false);
            }
        }

        return false;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUseEntity(UseEntityEvent event) {
        if (Config.enableWorldGuardIntegration) {
            Player player = event.getPlayer();

            if (event.getOriginalEvent() instanceof PlayerInteractAtEntityEvent) {
                PlayerInteractAtEntityEvent orig = (PlayerInteractAtEntityEvent) event.getOriginalEvent();
                Entity e = orig.getRightClicked();

                if (e.getType() == EntityType.ARMOR_STAND) {
                    if (!Hologram.isPartOfHologram((ArmorStand) e))
                        return;

                    for (Shop shop : plugin.getShopUtils().getShops()) {
                        if (shop.getHologram() != null && shop.getHologram().contains((ArmorStand) e)) {
                            if (isAllowed(player, shop.getLocation(), Action.RIGHT_CLICK_BLOCK)) {
                                event.setResult(Result.ALLOW);
                                orig.setCancelled(false);
                            }

                            return;
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamageEntity(DamageEntityEvent event) {
        if (Config.enableWorldGuardIntegration) {
            Player player = event.getPlayer();

            if (event.getOriginalEvent() instanceof EntityDamageByEntityEvent) {
                EntityDamageByEntityEvent orig = (EntityDamageByEntityEvent) event.getOriginalEvent();
                Entity e = orig.getEntity();

                if (e.getType() == EntityType.ARMOR_STAND) {
                    if (!Hologram.isPartOfHologram((ArmorStand) e))
                        return;

                    for (Shop shop : plugin.getShopUtils().getShops()) {
                        if (shop.getHologram() != null && shop.getHologram().contains((ArmorStand) e)) {
                            if (isAllowed(player, shop.getLocation(), Action.LEFT_CLICK_BLOCK)) {
                                event.setResult(Result.ALLOW);
                                orig.setCancelled(false);
                            }

                            return;
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUseBlock(UseBlockEvent event) {
        if (Config.enableWorldGuardIntegration) {
            Player player = event.getPlayer();

            if (event.getOriginalEvent() instanceof PlayerInteractEvent) {
                PlayerInteractEvent orig = (PlayerInteractEvent) event.getOriginalEvent();

                if (orig.hasBlock()) {
                    Material type = orig.getClickedBlock().getType();
                    if (type == Material.CHEST || type == Material.TRAPPED_CHEST) {
                        if (isAllowed(player, orig.getClickedBlock().getLocation(), orig.getAction())) {
                            event.setResult(Result.ALLOW);

                            ClickType ct = ClickType.getPlayerClickType(player);
                            if (!(ct != null && ct.getClickType() == ClickType.EnumClickType.CREATE)) {
                                // Don't un-cancel original event when trying to create shop.
                                // Flag "chest-access" has to be allowed too, so the original event won't be cancelled.
                                orig.setCancelled(false);
                            }
                        }
                    }
                }
            } else if (event.getOriginalEvent() instanceof InventoryOpenEvent) {
                InventoryOpenEvent orig = (InventoryOpenEvent) event.getOriginalEvent();

                if (orig.getInventory().getHolder() instanceof Chest) {
                    if (isAllowed(player, ((Chest)orig.getInventory().getHolder()).getLocation(), Action.RIGHT_CLICK_BLOCK)) {
                        event.setResult(Result.ALLOW);

                        ClickType ct = ClickType.getPlayerClickType(player);
                        if (!(ct != null && ct.getClickType() == ClickType.EnumClickType.CREATE)) {
                            // Don't un-cancel original event when trying to create shop.
                            // Flag "chest-access" has to be allowed too, so the original event won't be cancelled.
                            orig.setCancelled(false);
                        }
                    }
                }
            }
        }
    }

}
