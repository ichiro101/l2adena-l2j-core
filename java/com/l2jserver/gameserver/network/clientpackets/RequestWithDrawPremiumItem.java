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

import com.l2jserver.Config;
import com.l2jserver.gameserver.model.L2PremiumItem;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.ExGetPremiumItemList;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;
import com.l2jserver.gameserver.util.Util;

/**
 * @author Gnacik
 */
public final class RequestWithDrawPremiumItem extends L2GameClientPacket
{
	private static final String _C__D0_52_REQUESTWITHDRAWPREMIUMITEM = "[C] D0:52 RequestWithDrawPremiumItem";
	
	private int _itemNum;
	private int _charId;
	private long _itemcount;
	
	@Override
	protected void readImpl()
	{
		_itemNum = readD();
		_charId = readD();
		_itemcount = readQ();
	}
	
	@Override
	protected void runImpl()
	{
		final L2PcInstance activeChar = getClient().getActiveChar();
		
		if (activeChar == null)
			return;
		if (_itemcount <= 0)
			return;
		
		if (activeChar.getObjectId() != _charId)
		{
			Util.handleIllegalPlayerAction(activeChar, "[RequestWithDrawPremiumItem] Incorrect owner, Player: " + activeChar.getName(), Config.DEFAULT_PUNISH);
			return;
		}
		if (activeChar.getPremiumItemList().isEmpty())
		{
			Util.handleIllegalPlayerAction(activeChar, "[RequestWithDrawPremiumItem] Player: " + activeChar.getName() + " try to get item with empty list!", Config.DEFAULT_PUNISH);
			return;
		}
		if (activeChar.getWeightPenalty() >= 3 || !activeChar.isInventoryUnder80(false))
		{
			activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_CANNOT_RECEIVE_THE_VITAMIN_ITEM));
			return;
		}
		if (activeChar.isProcessingTransaction())
		{
			activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_CANNOT_RECEIVE_A_VITAMIN_ITEM_DURING_AN_EXCHANGE));
			return;
		}
		
		L2PremiumItem _item = activeChar.getPremiumItemList().get(_itemNum);
		if (_item == null)
			return;
		if (_item.getCount() < _itemcount)
			return;
		
		activeChar.addItem("PremiumItem", _item.getItemId(), _itemcount, null, true);
		
		if(_itemcount < _item.getCount() )
		{
			activeChar.getPremiumItemList().get(_itemNum).updateCount(_item.getCount()-_itemcount);
			activeChar.updatePremiumItem(_itemNum, _item.getCount()-_itemcount);
		}
		else
		{
			activeChar.getPremiumItemList().remove(_itemNum);
			activeChar.deletePremiumItem(_itemNum);
		}
		
		if(activeChar.getPremiumItemList().isEmpty())
			activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.THERE_ARE_NO_MORE_VITAMIN_ITEMS_TO_BE_FOUND));
		else
			activeChar.sendPacket(new ExGetPremiumItemList(activeChar));
	}
	
	@Override
	public String getType()
	{
		return _C__D0_52_REQUESTWITHDRAWPREMIUMITEM;
	}
}
