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

import java.util.Collection;

import com.l2jserver.Config;
import com.l2jserver.gameserver.model.L2TradeList;
import com.l2jserver.gameserver.model.L2TradeList.L2TradeItem;


/**
 * sample
 *
 * 1d
 * 1e 00 00 00 			// ??
 * 5c 4a a0 7c 			// buy list id
 * 02 00				// item count
 *
 * 04 00 				// itemType1  0-weapon/ring/earring/necklace  1-armor/shield  4-item/questitem/adena
 * 00 00 00 00 			// objectid
 * 32 04 00 00 			// itemid
 * 00 00 00 00 			// count
 * 05 00 				// itemType2  0-weapon  1-shield/armor  2-ring/earring/necklace  3-questitem  4-adena  5-item
 * 00 00
 * 60 09 00 00			// price
 *
 * 00 00
 * 00 00 00 00
 * b6 00 00 00
 * 00 00 00 00
 * 00 00
 * 00 00
 * 80 00 				//	body slot 	 these 4 values are only used if itemtype1 = 0 or 1
 * 00 00 				//
 * 00 00 				//
 * 00 00 				//
 * 50 c6 0c 00
 *

 * format   dd h (h dddhh hhhh d)	revision 377
 * format   dd h (h dddhh dhhh d)	revision 377
 *
 * @version $Revision: 1.4.2.1.2.3 $ $Date: 2005/03/27 15:29:57 $
 */
public final class BuyList extends L2GameServerPacket
{
	private static final String _S__1D_BUYLIST = "[S] 07 BuyList";
	private int _listId;
	private Collection<L2TradeItem> _list;
	private long _money;
	private double _taxRate = 0;
	
	public BuyList(L2TradeList list, long currentMoney, double taxRate)
	{
		_listId = list.getListId();
		_list = list.getItems();
		_money = currentMoney;
		_taxRate = taxRate;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xFE);
		writeH(0xB7);
		writeD(0x00);
		writeQ(_money);		// current money
		writeD(_listId);
		
		writeH(_list.size());
		
		for (L2TradeItem item : _list)
		{
			if (item.getCurrentCount() > 0 || !item.hasLimitedStock())
			{
				writeD(item.getItemId());
				writeD(item.getItemId());
				writeD(0);
				writeQ(item.getCurrentCount() < 0 ? 0 : item.getCurrentCount());
				writeH(item.getTemplate().getType2());
				writeH(item.getTemplate().getType1());	// Custom Type 1
				writeH(0x00);	// isEquipped
				writeD(item.getTemplate().getBodyPart());	// Body Part
				writeH(0x00);	// Enchant
				writeH(0x00);	// Custom Type
				writeD(0x00);	// Augment
				writeD(-1);		// Mana
				writeD(-9999);	// Time
				writeH(0x00);	// Element Type
				writeH(0x00);	// Element Power
				for (byte i = 0; i < 6; i++)
				{
					writeH(0x00);
				}
				// Enchant Effects
				writeH(0x00);
				writeH(0x00);
				writeH(0x00);
				
				if (item.getItemId() >= 3960 && item.getItemId() <= 4026)// Config.RATE_SIEGE_GUARDS_PRICE-//'
					writeQ((long) (item.getPrice() * Config.RATE_SIEGE_GUARDS_PRICE * (1 + _taxRate)));
				else
					writeQ((long) (item.getPrice() * (1 + _taxRate)));
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__1D_BUYLIST;
	}
}
