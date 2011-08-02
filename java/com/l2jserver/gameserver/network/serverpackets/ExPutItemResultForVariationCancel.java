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

import com.l2jserver.gameserver.model.L2ItemInstance;

/**
 * Format: (ch)ddd
 *
 */
public class ExPutItemResultForVariationCancel extends L2GameServerPacket
{
	private static final String _S__FE_56_EXCONFIRMCANCELITEM = "[S] FE:57 ExPutItemResultForVariationCancel";
	
	private int _itemObjId;
	private int _itemId;
	private int _itemAug1;
	private int _itemAug2;
	private int _price;
	
	public ExPutItemResultForVariationCancel(L2ItemInstance item, int price)
	{
		_itemObjId = item.getObjectId();
		_itemId = item.getItemId();
		_price = price;
		_itemAug1 = ((short)item.getAugmentation().getAugmentationId());
		_itemAug2 = item.getAugmentation().getAugmentationId() >> 16;
	}
	
	/**
	 * @see com.l2jserver.util.network.BaseSendablePacket.ServerBasePacket#writeImpl()
	 */
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x57);
		writeD(_itemObjId);
		writeD(_itemId);
		writeD(_itemAug1);
		writeD(_itemAug2);
		writeQ(_price);
		writeD(0x01);
	}
	
	/**
	 * @see com.l2jserver.gameserver.BasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__FE_56_EXCONFIRMCANCELITEM;
	}
	
}
