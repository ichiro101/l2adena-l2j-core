/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jserver.gameserver.network.clientpackets;

import java.util.logging.Logger;

import com.l2jserver.Config;
import com.l2jserver.gameserver.model.L2ItemInstance;
import com.l2jserver.gameserver.model.L2Skill;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.base.Race;
import com.l2jserver.gameserver.model.itemcontainer.PcInventory;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.ActionFailed;
import com.l2jserver.gameserver.network.serverpackets.InventoryUpdate;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;
import com.l2jserver.gameserver.templates.item.L2Item;
import com.l2jserver.gameserver.util.Util;

/**
 * This class ...
 *
 * @version $Revision: 1.2.2.3.2.5 $ $Date: 2005/03/27 15:29:30 $
 */
public final class RequestCrystallizeItem extends L2GameClientPacket
{
	private static final String _C__2F_REQUESTDCRYSTALLIZEITEM = "[C] 2F RequestCrystallizeItem";
	
	private static Logger _log = Logger.getLogger(RequestCrystallizeItem.class.getName());
	
	private int _objectId;
	private long _count;
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
		_count = readQ();
	}
	
	@Override
	protected void runImpl()
	{
		L2PcInstance activeChar = getClient().getActiveChar();
		
		if (activeChar == null)
		{
			_log.fine("RequestCrystalizeItem: activeChar was null");
			return;
		}
		
		if (!getClient().getFloodProtectors().getTransaction().tryPerformAction("crystallize"))
		{
			activeChar.sendMessage("You crystallizing too fast.");
			return;
		}
		
		if (_count <= 0)
		{
			Util.handleIllegalPlayerAction(activeChar, "[RequestCrystallizeItem] count <= 0! ban! oid: " + _objectId + " owner: " + activeChar.getName(), Config.DEFAULT_PUNISH);
			return;
		}
		
		if (activeChar.getPrivateStoreType() != 0 || activeChar.isInCrystallize())
		{
			activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANNOT_TRADE_DISCARD_DROP_ITEM_WHILE_IN_SHOPMODE));
			return;
		}
		
		int skillLevel = activeChar.getSkillLevel(L2Skill.SKILL_CRYSTALLIZE);
		if (skillLevel <= 0)
		{
			activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CRYSTALLIZE_LEVEL_TOO_LOW));
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			if (activeChar.getRace() != Race.Dwarf && activeChar.getClassId().ordinal() != 117 && activeChar.getClassId().ordinal() != 55)
				_log.info("Player "+activeChar.getClient()+" used crystalize with classid: "+activeChar.getClassId().ordinal());
			return;
		}
		
		PcInventory inventory = activeChar.getInventory();
		if (inventory != null)
		{
			L2ItemInstance item = inventory.getItemByObjectId(_objectId);
			if (item == null)
			{
				activeChar.sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			
			if (item.isHeroItem())
				return;
			
			if (_count > item.getCount())
				_count = activeChar.getInventory().getItemByObjectId(_objectId).getCount();
		}
		
		L2ItemInstance itemToRemove = activeChar.getInventory().getItemByObjectId(_objectId);
		if (itemToRemove == null
				|| itemToRemove.isShadowItem()
				|| itemToRemove.isTimeLimitedItem())
			return;
		
		if (!itemToRemove.getItem().isCrystallizable()
				|| (itemToRemove.getItem().getCrystalCount() <= 0)
				|| (itemToRemove.getItem().getCrystalType() == L2Item.CRYSTAL_NONE))
		{
			_log.warning(activeChar.getName() + " (" + activeChar.getObjectId() + ") tried to crystallize " + itemToRemove.getItem().getItemId());
			return;
		}
		
		if (!activeChar.getInventory().canManipulateWithItemId(itemToRemove.getItemId()))
		{
			activeChar.sendMessage("Cannot use this item.");
			return;
		}
		
		// Check if the char can crystallize items and return if false;
		boolean canCrystallize = true;
		
		switch (itemToRemove.getItem().getItemGradeSPlus())
		{
			case L2Item.CRYSTAL_C:
			{
				if (skillLevel <= 1)
					canCrystallize = false;
				break;
			}
			case L2Item.CRYSTAL_B:
			{
				if (skillLevel <= 2)
					canCrystallize = false;
				break;
			}
			case L2Item.CRYSTAL_A:
			{
				if (skillLevel <= 3)
					canCrystallize = false;
				break;
			}
			case L2Item.CRYSTAL_S:
			{
				if (skillLevel <= 4)
					canCrystallize = false;
				break;
			}
		}
		
		if (!canCrystallize)
		{
			activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CRYSTALLIZE_LEVEL_TOO_LOW));
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		activeChar.setInCrystallize(true);
		
		// unequip if needed
		if (itemToRemove.isEquipped())
		{
			L2ItemInstance[] unequiped = activeChar.getInventory().unEquipItemInSlotAndRecord(itemToRemove.getLocationSlot());
			InventoryUpdate iu = new InventoryUpdate();
			for (L2ItemInstance item: unequiped)
				iu.addModifiedItem(item);
			activeChar.sendPacket(iu);
			
			SystemMessage msg;
			if (itemToRemove.getEnchantLevel() > 0)
			{
				msg = SystemMessage.getSystemMessage(SystemMessageId.EQUIPMENT_S1_S2_REMOVED);
				msg.addNumber(itemToRemove.getEnchantLevel());
				msg.addItemName(itemToRemove);
			}
			else
			{
				msg = SystemMessage.getSystemMessage(SystemMessageId.S1_DISARMED);
				msg.addItemName(itemToRemove);
			}
			activeChar.sendPacket(msg);
		}
		
		// remove from inventory
		L2ItemInstance removedItem = activeChar.getInventory().destroyItem("Crystalize", _objectId, _count, activeChar, null);
		
		InventoryUpdate iu = new InventoryUpdate();
		iu.addRemovedItem(removedItem);
		activeChar.sendPacket(iu);
		
		// add crystals
		int crystalId = itemToRemove.getItem().getCrystalItemId();
		int crystalAmount = itemToRemove.getCrystalCount();
		L2ItemInstance createditem = activeChar.getInventory().addItem("Crystalize", crystalId, crystalAmount, activeChar, activeChar);
		
		SystemMessage sm;
		sm = SystemMessage.getSystemMessage(SystemMessageId.S1_CRYSTALLIZED);
		sm.addItemName(removedItem);
		activeChar.sendPacket(sm);
		
		sm = SystemMessage.getSystemMessage(SystemMessageId.EARNED_S2_S1_S);
		sm.addItemName(createditem);
		sm.addItemNumber(crystalAmount);
		activeChar.sendPacket(sm);
		
		activeChar.broadcastUserInfo();
		
		L2World world = L2World.getInstance();
		world.removeObject(removedItem);
		
		activeChar.setInCrystallize(false);
	}
	
	@Override
	public String getType()
	{
		return _C__2F_REQUESTDCRYSTALLIZEITEM;
	}
}
