package com.nisovin.shopkeepers.shopcreation;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import com.nisovin.shopkeepers.api.ShopkeepersPlugin;
import com.nisovin.shopkeepers.api.shopkeeper.ShopCreationData;
import com.nisovin.shopkeepers.api.shopkeeper.ShopType;
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.api.shopkeeper.player.PlayerShopCreationData;
import com.nisovin.shopkeepers.api.shopkeeper.player.PlayerShopType;
import com.nisovin.shopkeepers.api.shopobjects.ShopObjectType;
import com.nisovin.shopkeepers.config.Settings;
import com.nisovin.shopkeepers.container.ShopContainers;
import com.nisovin.shopkeepers.lang.Messages;
import com.nisovin.shopkeepers.util.EventUtils;
import com.nisovin.shopkeepers.util.ItemUtils;
import com.nisovin.shopkeepers.util.Log;
import com.nisovin.shopkeepers.util.PermissionUtils;
import com.nisovin.shopkeepers.util.TestPlayerInteractEvent;
import com.nisovin.shopkeepers.util.TextUtils;

/**
 * Handles the usage of the shop creation item.
 */
class CreateListener implements Listener {

	private final SKShopkeepersPlugin plugin;
	private final ShopkeeperCreation shopkeeperCreation;

	CreateListener(SKShopkeepersPlugin plugin, ShopkeeperCreation shopkeeperCreation) {
		this.plugin = plugin;
		this.shopkeeperCreation = shopkeeperCreation;
	}

	void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, plugin);
		// Ensure that our interact event handler is always executed first, even after plugin reloads:
		// In order to not change the order among the already registered event handlers of our own plugin, we move them
		// all together to the front of the handler list.
		EventUtils.enforceExecuteFirst(PlayerInteractEvent.class, EventPriority.LOWEST, plugin);
	}

	void onDisable() {
		HandlerList.unregisterAll(this);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	void onItemHeld(PlayerItemHeldEvent event) {
		Player player = event.getPlayer();
		ItemStack newItemInHand = player.getInventory().getItem(event.getNewSlot());
		if (!Settings.isShopCreationItem(newItemInHand)) {
			return;
		}

		if (!plugin.hasCreatePermission(player)) {
			// The player cannot create any shopkeepers at all.
			return;
		}

		// Print info message about usage:
		TextUtils.sendMessage(player, Messages.creationItemSelected);
	}

	// See LivingEntityShopListener for a reasoning on why we handle this the event the way we do:
	// We handle and cancel this event on LOWEST priority so that other plugins (eg. protection plugins) can ignore it.
	// This event handler might check container access by calling another dummy interaction event. Handling and
	// canceling this event as early as possible is also required so that other event handlers (for example of
	// protection plugins) can ignore the first event and don't handle the interaction twice.
	// To resolve conflicts with other event handlers at LOWEST priority, we ignore the event if it is already
	// cancelled. This includes, for example, the interaction handler for sign shops within this plugin, which takes
	// precedence over this event handler.
	// To further reduce conflicts with other event handlers at LOWEST priority, including after dynamic plugin reloads,
	// we forcefully move our event handler(s) to the front of the handler list.
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	void onPlayerInteract(PlayerInteractEvent event) {
		// Ignore our own fake interact event:
		if (event instanceof TestPlayerInteractEvent) return;

		// Ignore if the player isn't right-clicking, or left-clicking air:
		Action action = event.getAction();
		if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_AIR) {
			return;
		}

		// Make sure that the used item is the shop creation item:
		ItemStack itemInHand = event.getItem();
		if (!Settings.isShopCreationItem(itemInHand)) {
			return;
		}

		Player player = event.getPlayer();
		Log.debug(() -> "Player " + player.getName() + " is interacting with the shop creation item");

		// Capture event's cancellation state:
		Result useItemInHand = event.useItemInHand();

		// Prevent regular usage:
		// TODO Are there items which would require canceling the event for all left clicks or physical interaction as
		// well?
		if (Settings.preventShopCreationItemRegularUsage && !PermissionUtils.hasPermission(player, ShopkeepersPlugin.BYPASS_PERMISSION)) {
			Log.debug("  Preventing normal shop creation item usage");
			event.setCancelled(true);
		}

		// Ignore off-hand interactions from this point on:
		// -> The item will only act as shop creation item if it is held in the main hand.
		if (event.getHand() != EquipmentSlot.HAND) {
			Log.debug("  Ignoring off-hand interaction");
			return;
		}

		// Ignore if already cancelled. Resolves conflicts with other event handlers running at LOWEST priority (eg.
		// Shopkeepers' sign shop listener acts on LOWEST priority as well).
		if (useItemInHand == Result.DENY) {
			Log.debug("  Ignoring already cancelled item interaction");
			return;
		}

		// Cancel interactions with our custom shop creation item:
		event.setCancelled(true);

		// Get shop type:
		ShopType<?> shopType = plugin.getShopTypeRegistry().getSelection(player);
		// Get shop object type:
		ShopObjectType<?> shopObjType = plugin.getShopObjectTypeRegistry().getSelection(player);

		if (shopType == null || shopObjType == null) {
			// The player cannot create any shops at all:
			TextUtils.sendMessage(player, Messages.noPermission);
			return;
		}

		// Check what the player is doing with the shop creation item in hand:
		if (action == Action.RIGHT_CLICK_AIR) {
			if (player.isSneaking()) {
				// Cycle shop objects:
				plugin.getShopObjectTypeRegistry().selectNext(player);
			} else {
				// Cycle shopkeeper types:
				plugin.getShopTypeRegistry().selectNext(player);
			}
		} else if (action == Action.LEFT_CLICK_AIR) {
			if (player.isSneaking()) {
				// Cycle shop objects backwards:
				plugin.getShopObjectTypeRegistry().selectPrevious(player);
			} else {
				// Cycle shopkeeper types backwards:
				plugin.getShopTypeRegistry().selectPrevious(player);
			}
		} else if (action == Action.RIGHT_CLICK_BLOCK) {
			Block clickedBlock = event.getClickedBlock();

			Block selectedContainer = shopkeeperCreation.getSelectedContainer(player);
			// Validate old selected container:
			if (selectedContainer != null && !ShopContainers.isSupportedContainer(selectedContainer.getType())) {
				shopkeeperCreation.selectContainer(player, null);
				selectedContainer = null;
			}

			// Handle container selection:
			boolean isContainerSelection = false;
			if (!clickedBlock.equals(selectedContainer)) {
				if (ShopContainers.isSupportedContainer(clickedBlock.getType())) {
					isContainerSelection = true;
					// Check if the container can be used for a shop:
					if (shopkeeperCreation.handleCheckContainer(player, clickedBlock)) {
						// Select container:
						shopkeeperCreation.selectContainer(player, clickedBlock);
						TextUtils.sendMessage(player, Messages.containerSelected);
					}
				} else if (ItemUtils.isContainer(clickedBlock.getType())) {
					// Player clicked a type of container which cannot be used for shops:
					isContainerSelection = true;
					TextUtils.sendMessage(player, Messages.unsupportedContainer);
				}
			}

			if (!isContainerSelection) {
				// Player shop creation:
				if (selectedContainer == null) {
					// Clicked a location without having a container selected:
					TextUtils.sendMessage(player, Messages.mustSelectContainer);
					return;
				}
				assert ShopContainers.isSupportedContainer(selectedContainer.getType()); // Checked above already

				// Only player shops can be selected currently. TODO Change that?
				assert shopType instanceof PlayerShopType;

				// Determine spawn location:
				BlockFace clickedBlockFace = event.getBlockFace();
				Location spawnLocation = shopkeeperCreation.determineSpawnLocation(player, clickedBlock, clickedBlockFace);

				// Create player shopkeeper:
				ShopCreationData creationData = PlayerShopCreationData.create(player, shopType, shopObjType, spawnLocation, clickedBlockFace, selectedContainer);
				Shopkeeper shopkeeper = plugin.handleShopkeeperCreation(creationData);
				if (shopkeeper != null) {
					// Shopkeeper creation was successful:

					// Reset selected container:
					shopkeeperCreation.selectContainer(player, null);

					// Manually remove creation item from player's hand after this event is processed:
					Bukkit.getScheduler().runTask(plugin, () -> {
						ItemStack newItemInMainHand = ItemUtils.descreaseItemAmount(itemInHand, 1);
						player.getInventory().setItemInMainHand(newItemInMainHand);
					});
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onBlockDispense(BlockDispenseEvent event) {
		if (Settings.preventShopCreationItemRegularUsage && Settings.isShopCreationItem(event.getItem())) {
			Log.debug(() -> "Preventing dispensing of shop creation item at " + TextUtils.getLocationString(event.getBlock()));
			event.setCancelled(true);
			// TODO Drop item instead.
			// TODO Only prevent it for items that have a special dispense behavior.
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
		this.handleEntityInteraction(event);
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
		this.handleEntityInteraction(event);
	}

	private void handleEntityInteraction(PlayerInteractEntityEvent event) {
		if (!Settings.preventShopCreationItemRegularUsage) return;
		Player player = event.getPlayer();
		// We check the permission first since this check is fast:
		if (PermissionUtils.hasPermission(player, ShopkeepersPlugin.BYPASS_PERMISSION)) return;
		ItemStack itemInHand = ItemUtils.getItem(player.getInventory(), event.getHand());
		if (!Settings.isShopCreationItem(itemInHand)) return;

		// Prevent the entity interaction:
		// TODO Only prevent the entity interaction if the item actually has a special entity interaction behavior.
		// The interaction result may also depend on the interacted entity. However, there is no Bukkit API yet to
		// check for this.
		Log.debug(() -> {
			if (event instanceof PlayerInteractAtEntityEvent) {
				return "Preventing interaction at entity with shop creation item for player " + TextUtils.getPlayerString(player);
			} else {
				return "Preventing entity interaction with shop creation item for player " + TextUtils.getPlayerString(player);
			}
		});
		event.setCancelled(true);
	}

	// Rate limiting of debug messages related to the PrepareAnvilEvent:
	private static final long ANVIL_DEBUG_MESSAGE_THROTTLE_MILLISECONDS = 5000L; // 5 seconds
	private UUID lastAnvilDebugMessagePlayer = null;
	private long lastAnvilDebugMessageTime = 0L; // System time in milliseconds

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onPrepareAnvilEvent(PrepareAnvilEvent event) {
		if (!Settings.preventShopCreationItemRegularUsage) return;
		if (ItemUtils.isEmpty(event.getResult())) {
			// The PrepareAnvilEvent is called multiple times. We only need to handle it when a result item is set.
			return;
		}

		Inventory anvilInventory = event.getInventory();
		if (!Settings.isShopCreationItem(anvilInventory.getItem(0)) && !Settings.isShopCreationItem(anvilInventory.getItem(1))) {
			// No shop creation item involved.
			// Note: We only prevent the renaming of the shop creation item, not its creation. I.e. we do not check the
			// result item here. The shop creation item could intentionally be set up to be an item that can be created
			// with an anvil.
			return;
		}

		if (Settings.debug) {
			// Rate limiting of debug messages (this event is called for every text change):
			Player player = (Player) event.getView().getPlayer();
			UUID playerUniqueId = player.getUniqueId();
			long now = System.currentTimeMillis();
			if (!playerUniqueId.equals(lastAnvilDebugMessagePlayer) || (now - lastAnvilDebugMessageTime) > ANVIL_DEBUG_MESSAGE_THROTTLE_MILLISECONDS) {
				lastAnvilDebugMessagePlayer = playerUniqueId;
				lastAnvilDebugMessageTime = now;
				Log.debug(() -> "Preventing renaming of shop creation item by " + player.getName() + " (debug output is throttled)");
			}
		}
		event.setResult(null);
		ItemUtils.updateInventoryLater(anvilInventory);
		// TODO Inform the player? (This would require some per-player throttling)
	}
}
