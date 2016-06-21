package com.aionemu.gameserver.model.templates.item.actions;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import com.aionemu.gameserver.model.gameobjects.HouseObject;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_HOUSE_EDIT;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_UPDATE_PLAYER_APPEARANCE;
import com.aionemu.gameserver.services.item.ItemPacketService;
import com.aionemu.gameserver.utils.ChatUtil;
import com.aionemu.gameserver.utils.PacketSendUtility;

/**
 * @author IceReaper
 * @modified Neon
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DyeAction")
public class DyeAction extends AbstractItemAction implements IHouseObjectDyeAction {

	@XmlAttribute(name = "color")
	protected String color;
	@XmlAttribute
	private Integer minutes;

	@Override
	public boolean canAct(Player player, Item parentItem, Item targetItem) {
		if (targetItem == null) { // no item selected.
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_ITEM_COLOR_ERROR());
			return false;
		}

		return true;
	}

	@Override
	public void act(Player player, Item parentItem, Item targetItem) {
		if (!targetItem.getItemSkinTemplate().isItemDyePermitted())
			return;
		if (!player.getInventory().decreaseByObjectId(parentItem.getObjectId(), 1))
			return;
		targetItem.setItemColor(getColor());
		if (minutes != null)
			targetItem.setColorExpireTime((int) (System.currentTimeMillis() / 1000 + minutes * 60));
		else
			targetItem.setColorExpireTime(0);
		if (targetItem.getItemColor() == null) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_ITEM_COLOR_REMOVE_SUCCEED(ChatUtil.nameId(targetItem.getNameId())));
		} else {
			PacketSendUtility.sendPacket(player,
				SM_SYSTEM_MESSAGE.STR_ITEM_COLOR_CHANGE_SUCCEED(ChatUtil.nameId(targetItem.getNameId()), ChatUtil.nameId(parentItem.getNameId())));
		}

		// item is equipped, so need broadcast packet
		if (player.getEquipment().getEquippedItemByObjId(targetItem.getObjectId()) != null) {
			PacketSendUtility.broadcastPacket(player,
				new SM_UPDATE_PLAYER_APPEARANCE(player.getObjectId(), player.getEquipment().getEquippedForAppearence()), true);
			player.getEquipment().setPersistentState(PersistentState.UPDATE_REQUIRED);
		} else { // item is not equipped
			player.getInventory().setPersistentState(PersistentState.UPDATE_REQUIRED);
		}

		ItemPacketService.updateItemAfterInfoChange(player, targetItem);
	}

	public Integer getColor() {
		return color.equals("no") ? null : Integer.parseInt(color, 16);
	}

	@Override
	public boolean canAct(Player player, Item parentItem, HouseObject<?> targetHouseObject) {
		if (targetHouseObject == null) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_ITEM_COLOR_ERROR());
			return false;
		}
		if (color.equals("no") && targetHouseObject.getColor() == null) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_ITEM_PAINT_ERROR_CANNOTREMOVE());
			return false;
		}
		boolean canPaint = targetHouseObject.getObjectTemplate().getCanDye();
		if (!canPaint)
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_ITEM_PAINT_ERROR_CANNOTPAINT());
		return canPaint;
	}

	@Override
	public void act(Player player, Item parentItem, HouseObject<?> targetHouseObject) {
		if (!player.getInventory().decreaseByObjectId(parentItem.getObjectId(), 1))
			return;
		targetHouseObject.setColor(getColor());
		float x = targetHouseObject.getX();
		float y = targetHouseObject.getY();
		float z = targetHouseObject.getZ();
		int rotation = targetHouseObject.getRotation();
		PacketSendUtility.sendPacket(player, new SM_HOUSE_EDIT(7, 0, targetHouseObject.getObjectId()));
		PacketSendUtility.sendPacket(player, new SM_HOUSE_EDIT(5, targetHouseObject.getObjectId(), x, y, z, rotation));
		targetHouseObject.spawn();
		int objectName = targetHouseObject.getObjectTemplate().getNameId();
		if (targetHouseObject.getColor() == null) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_ITEM_PAINT_REMOVE_SUCCEED(objectName));
		} else {
			int paintName = parentItem.getItemTemplate().getNameId();
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_ITEM_PAINT_SUCCEED(objectName, paintName));
		}
	}

}
