package com.nisovin.shopkeepers.shopobjects.living.types;

import java.util.List;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Shulker;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.api.shopkeeper.ShopCreationData;
import com.nisovin.shopkeepers.lang.Messages;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.shopobjects.ShopObjectData;
import com.nisovin.shopkeepers.shopobjects.living.LivingShops;
import com.nisovin.shopkeepers.shopobjects.living.SKLivingShopObject;
import com.nisovin.shopkeepers.shopobjects.living.SKLivingShopObjectType;
import com.nisovin.shopkeepers.ui.editor.Button;
import com.nisovin.shopkeepers.ui.editor.EditorSession;
import com.nisovin.shopkeepers.ui.editor.ShopkeeperActionButton;
import com.nisovin.shopkeepers.util.bukkit.BlockFaceUtils;
import com.nisovin.shopkeepers.util.data.property.BasicProperty;
import com.nisovin.shopkeepers.util.data.property.Property;
import com.nisovin.shopkeepers.util.data.property.value.PropertyValue;
import com.nisovin.shopkeepers.util.data.serialization.InvalidDataException;
import com.nisovin.shopkeepers.util.data.serialization.java.EnumSerializers;
import com.nisovin.shopkeepers.util.inventory.ItemUtils;
import com.nisovin.shopkeepers.util.java.EnumUtils;
import com.nisovin.shopkeepers.util.java.Validate;

public class ShulkerShop extends SKLivingShopObject<@NonNull Shulker> {

	public static final Property<@Nullable DyeColor> COLOR = new BasicProperty<@Nullable DyeColor>()
			.dataKeyAccessor("color", EnumSerializers.lenient(DyeColor.class))
			.nullable() // Null indicates default color
			.defaultValue(null)
			.build();

	public static final Property<@NonNull BlockFace> ATTACHED_FACE = new BasicProperty<@NonNull BlockFace>()
			.dataKeyAccessor("attachedFace", EnumSerializers.lenient(BlockFace.class))
			.validator(value -> {
				Validate.isTrue(BlockFaceUtils.isBlockSide(value),
						"Not a valid block side: '" + value + "'.");
			})
			// For versions before 2.16.0:
			.useDefaultIfMissing()
			.defaultValue(BlockFace.UP)
			.build();

	private final PropertyValue<@Nullable DyeColor> colorProperty = new PropertyValue<>(COLOR)
			.onValueChanged(Unsafe.initialized(this)::applyColor)
			.build(properties);
	private final PropertyValue<@NonNull BlockFace> attachedFaceProperty = new PropertyValue<>(ATTACHED_FACE)
			.onValueChanged(Unsafe.initialized(this)::applyAttachedFace)
			.build(properties);

	public ShulkerShop(
			LivingShops livingShops,
			SKLivingShopObjectType<@NonNull ShulkerShop> livingObjectType,
			AbstractShopkeeper shopkeeper,
			@Nullable ShopCreationData creationData
	) {
		super(livingShops, livingObjectType, shopkeeper, creationData);
	}

	@Override
	public void load(ShopObjectData shopObjectData) throws InvalidDataException {
		super.load(shopObjectData);
		colorProperty.load(shopObjectData);
		attachedFaceProperty.load(shopObjectData);
	}

	@Override
	public void save(ShopObjectData shopObjectData, boolean saveAll) {
		super.save(shopObjectData, saveAll);
		colorProperty.save(shopObjectData);
		attachedFaceProperty.save(shopObjectData);
	}

	@Override
	protected void onSpawn() {
		super.onSpawn();
		this.applyColor();
		this.applyAttachedFace();
	}

	@Override
	public List<@NonNull Button> createEditorButtons() {
		List<@NonNull Button> editorButtons = super.createEditorButtons();
		editorButtons.add(this.getColorEditorButton());
		return editorButtons;
	}

	// COLOR

	public @Nullable DyeColor getColor() {
		return colorProperty.getValue();
	}

	public void setColor(@Nullable DyeColor color) {
		colorProperty.setValue(color);
	}

	public void cycleColor(boolean backwards) {
		this.setColor(
				EnumUtils.cycleEnumConstantNullable(DyeColor.class, this.getColor(), backwards)
		);
	}

	private void applyColor() {
		Shulker entity = this.getEntity();
		if (entity == null) return; // Not spawned
		// TODO Bukkit's Shulker interface does not specify the nullness
		entity.setColor(Unsafe.nullableAsNonNull(this.getColor()));
	}

	private ItemStack getColorEditorItem() {
		DyeColor color = this.getColor();
		ItemStack iconItem;
		if (color == null) {
			iconItem = new ItemStack(Material.PURPUR_BLOCK);
		} else {
			iconItem = new ItemStack(ItemUtils.getWoolType(color));
		}
		ItemUtils.setDisplayNameAndLore(
				iconItem,
				Messages.buttonShulkerColor,
				Messages.buttonShulkerColorLore
		);
		return iconItem;
	}

	private Button getColorEditorButton() {
		return new ShopkeeperActionButton() {
			@Override
			public @Nullable ItemStack getIcon(EditorSession editorSession) {
				return getColorEditorItem();
			}

			@Override
			protected boolean runAction(
					EditorSession editorSession,
					InventoryClickEvent clickEvent
			) {
				boolean backwards = clickEvent.isRightClick();
				cycleColor(backwards);
				return true;
			}
		};
	}

	// ATTACHED BLOCK FACE

	// Can be edited by moving the shopkeeper.
	@Override
	public void setAttachedBlockFace(BlockFace attachedBlockFace) {
		super.setAttachedBlockFace(attachedBlockFace);
		// The "attached face" of the shulker is the opposite of the block face the shulker is
		// attached against:
		this.setAttachedFace(attachedBlockFace.getOppositeFace());
	}

	public void setAttachedFace(BlockFace attachedFace) {
		attachedFaceProperty.setValue(attachedFace);
	}

	public BlockFace getAttachedFace() {
		return attachedFaceProperty.getValue();
	}

	private void applyAttachedFace() {
		Shulker entity = this.getEntity();
		if (entity == null) return; // Not spawned
		entity.setAttachedFace(this.getAttachedFace());
	}

	// TODO Open state (Peek)
}
