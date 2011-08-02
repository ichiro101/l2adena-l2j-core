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

import com.l2jserver.gameserver.model.Elementals;
import com.l2jserver.gameserver.model.L2ItemInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.ExBaseAttributeCancelResult;
import com.l2jserver.gameserver.network.serverpackets.InventoryUpdate;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;
import com.l2jserver.gameserver.network.serverpackets.UserInfo;
import com.l2jserver.gameserver.templates.item.L2Item;
import com.l2jserver.gameserver.templates.item.L2Weapon;

public class RequestExRemoveItemAttribute extends L2GameClientPacket
{
	private static String _C__D0_23_REQUESTEXREMOVEITEMATTRIBUTE = "[C] D0:23 RequestExRemoveItemAttribute";
	
	private int _objectId;
	private long _price;
	private byte _element;
	
	public RequestExRemoveItemAttribute()
	{
	}
	
	@Override
	public void readImpl()
	{
		_objectId = readD();
		_element = (byte) readD();
	}
	
	@Override
	public void runImpl()
	{
		L2PcInstance activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		L2ItemInstance targetItem = activeChar.getInventory().getItemByObjectId(_objectId);
		
		if (targetItem == null)
			return;
		
		if (targetItem.getElementals() == null || targetItem.getElemental(_element) == null)
			return;
		
		if (activeChar.reduceAdena("RemoveElement", getPrice(targetItem), activeChar, true))
		{
			if (targetItem.isEquipped())
				targetItem.getElemental(_element).removeBonus(activeChar);
			targetItem.clearElementAttr(_element);
			activeChar.sendPacket(new UserInfo(activeChar));
			
			InventoryUpdate iu = new InventoryUpdate();
			iu.addModifiedItem(targetItem);
			activeChar.sendPacket(iu);
			SystemMessage sm;
			byte realElement = targetItem.isArmor() ? Elementals.getOppositeElement(_element) : _element;
			if (targetItem.getEnchantLevel() > 0)
			{
				if (targetItem.isArmor())
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_S2_S3_ATTRIBUTE_REMOVED_RESISTANCE_TO_S4_DECREASED);
				else
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_S2_ELEMENTAL_POWER_REMOVED);
				sm.addNumber(targetItem.getEnchantLevel());
				sm.addItemName(targetItem);
				if (targetItem.isArmor())
				{
					sm.addElemntal(realElement);
					sm.addElemntal(Elementals.getOppositeElement(realElement));
				}
			}
			else
			{
				if (targetItem.isArmor())
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_S2_ATTRIBUTE_REMOVED_RESISTANCE_S3_DECREASED);
				else
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_ELEMENTAL_POWER_REMOVED);
				sm.addItemName(targetItem);
				if (targetItem.isArmor())
				{
					sm.addElemntal(realElement);
					sm.addElemntal(Elementals.getOppositeElement(realElement));
				}
			}
			activeChar.sendPacket(sm);
			activeChar.sendPacket(new ExBaseAttributeCancelResult(targetItem.getObjectId(), _element));
			return;
		}
		else
		{
			activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_DO_NOT_HAVE_ENOUGH_FUNDS_TO_CANCEL_ATTRIBUTE));
			return;
		}
	}
	
	private long getPrice(L2ItemInstance item)
	{
		switch(item.getItem().getCrystalType())
		{
			case L2Item.CRYSTAL_S:
				if (item.getItem() instanceof L2Weapon)
					_price = 50000;
				else
					_price = 40000;
				break;
			case L2Item.CRYSTAL_S80:
				if (item.getItem() instanceof L2Weapon)
					_price = 100000;
				else
					_price = 80000;
				break;
			case L2Item.CRYSTAL_S84:
				if (item.getItem() instanceof L2Weapon)
					_price = 200000;
				else
					_price = 160000;
				break;
		}
		
		return _price;
	}
	
	@Override
	public String getType()
	{
		return _C__D0_23_REQUESTEXREMOVEITEMATTRIBUTE;
	}
}