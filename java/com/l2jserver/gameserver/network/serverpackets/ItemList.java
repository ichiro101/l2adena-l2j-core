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
package com.l2jserver.gameserver.network.serverpackets;

import java.util.logging.Logger;

import javolution.util.FastList;

import com.l2jserver.gameserver.model.L2ItemInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.itemcontainer.PcInventory;


/**
 *
 *
 * sample
 *
 * 11 // packet ID
 * 00 00 // show window
 * 15 00 // item count
 * 
 * 04 00 // item type id
 * 
 * 48 B8 B9 40 // object id
 * 47 09 00 00 //item id
 * 0C 00 00 00 // location slot
 * 01 00 00 00 00 00 00 00 // amount
 * 05 00 // item type 2
 * 00 00 // custom type 1
 * 00 00 // is equipped?
 * 00 00 00 00 // body part
 * 0F 00 // enchant level
 * 00 00 // custom type 2
 * 00 00 // augmentation data
 * 00 00 // augmentation data
 * FF FF FF FF //mana
 * FE FF // attack element
 * 00 00 // attack element power
 * 00 00 // fire defence element power
 * 00 00 // water defence element power
 * 00 00 // wind defence element power
 * 00 00 // earth defence element power
 * 00 00 // holy defence element power
 * 00 00 // unholy defence element power
 * F1 D8 FF FF // remaining time = -9999
 *

 * format   hh (h dddQhhhdhhhhdhhhhhhhhd)
 *
 * @version $Revision: 1.4.2.1.2.4 $ $Date: 2005/03/27 15:29:57 $
 */
public final class ItemList extends L2GameServerPacket
{
	private static Logger _log = Logger.getLogger(ItemList.class.getName());
	private static final String _S__11_ITEMLIST = "[S] 11 ItemList";
	
	private PcInventory _inventory;
	private L2ItemInstance[] _items;
	private boolean _showWindow;
	private int length;
	private FastList<L2ItemInstance> questItems;
	
	public ItemList(L2PcInstance cha, boolean showWindow)
	{
		_inventory = cha.getInventory();
		_items = cha.getInventory().getItems();
		_showWindow = showWindow;
		questItems = FastList.newInstance();
		for (int i = 0; i < _items.length; i++)
		{
			if (_items[i] != null && _items[i].isQuestItem())
			{
				questItems.add(_items[i]); // add to questinv
				_items[i] = null; // remove from list
			}
			else
				length++; // increase size
		}
	}
	
	@SuppressWarnings("unused")
	private void showDebug()
	{
		for (L2ItemInstance temp : _items)
		{
			_log.fine("item:" + temp.getItem().getName() + " type1:" + temp.getItem().getType1() + " type2:" + temp.getItem().getType2());
		}
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x11);
		writeH(_showWindow ? 0x01 : 0x00);
		
		//int count = _items.length;
		writeH(length);
		
		for (L2ItemInstance temp : _items)
		{
			if (temp == null || temp.getItem() == null)
				continue;
			
			writeD(temp.getObjectId());
			writeD(temp.getItemId());
			writeD(temp.getLocationSlot());
			writeQ(temp.getCount());
			writeH(temp.getItem().getType2()); // item type2
			writeH(temp.getCustomType1()); // item type3
			writeH(temp.isEquipped() ? 0x01 : 0x00);
			writeD(temp.getItem().getBodyPart());
			writeH(temp.getEnchantLevel()); // enchant level
			//race tickets
			writeH(temp.getCustomType2()); // item type3
			if (temp.isAugmented())
				writeD(temp.getAugmentation().getAugmentationId());
			else
				writeD(0x00);
			writeD(temp.getMana());
			writeD(temp.isTimeLimitedItem() ? (int) (temp.getRemainingTime() / 1000) : -9999);
			writeH(temp.getAttackElementType());
			writeH(temp.getAttackElementPower());
			for (byte i = 0; i < 6; i++)
			{
				writeH(temp.getElementDefAttr(i));
			}
			// Enchant Effects
			writeH(0x00);
			writeH(0x00);
			writeH(0x00);
		}
		if (_inventory.hasInventoryBlock())
		{
			writeH(_inventory.getBlockItems().length);
			writeC(_inventory.getBlockMode());
			for(int i : _inventory.getBlockItems())
				writeD(i);
		}
		else
			writeH(0x00);
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__11_ITEMLIST;
	}
	
	@Override
	public void runImpl()
	{
		getClient().sendPacket(new ExQuestItemList(questItems, getClient().getActiveChar().getInventory()));
	}
}
