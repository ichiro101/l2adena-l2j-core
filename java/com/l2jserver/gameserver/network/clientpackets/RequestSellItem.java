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

import static com.l2jserver.gameserver.model.actor.L2Npc.INTERACTION_DISTANCE;
import static com.l2jserver.gameserver.model.itemcontainer.PcInventory.MAX_ADENA;

import java.util.List;

import com.l2jserver.Config;
import com.l2jserver.gameserver.TradeController;
import com.l2jserver.gameserver.model.L2ItemInstance;
import com.l2jserver.gameserver.model.L2Object;
import com.l2jserver.gameserver.model.L2TradeList;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.instance.L2MerchantInstance;
import com.l2jserver.gameserver.model.actor.instance.L2MerchantSummonInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.serverpackets.ActionFailed;
import com.l2jserver.gameserver.network.serverpackets.ExBuySellListPacket;
import com.l2jserver.gameserver.network.serverpackets.StatusUpdate;
import com.l2jserver.gameserver.util.Util;

/**
 * packet type id 0x1e
 *
 * sample
 *
 * 1e
 * 00 00 00 00		// list id
 * 02 00 00 00		// number of items
 *
 * 71 72 00 10		// object id
 * ea 05 00 00		// item id
 * 01 00 00 00		// item count
 *
 * 76 4b 00 10		// object id
 * 2e 0a 00 00		// item id
 * 01 00 00 00		// item count
 *
 * format:		cdd (ddd)
 * RequestSellItem client packet class.
 */
public final class RequestSellItem extends L2GameClientPacket
{
	private static final String _C__37_REQUESTSELLITEM = "[C] 37 RequestSellItem";
	//private static Logger _log = Logger.getLogger(RequestSellItem.class.getName());
	
	private static final int BATCH_LENGTH = 16; // length of the one item
	
	private int _listId;
	private Item[] _items = null;
	
	@Override
	protected void readImpl()
	{
		_listId = readD();
		int count = readD();
		if (count <= 0 || count > Config.MAX_ITEM_IN_PACKET || count * BATCH_LENGTH != _buf.remaining())
		{
			return;
		}
		
		_items = new Item[count];
		for (int i = 0; i < count; i++)
		{
			int objectId = readD();
			int itemId = readD();
			long cnt = readQ();
			if (objectId < 1 || itemId < 1 || cnt < 1)
			{
				_items = null;
				return;
			}
			_items[i] = new Item(objectId, itemId, cnt);
		}
	}
	
	@Override
	protected void runImpl()
	{
		this.processSell();
	}
	
	protected void processSell()
	{
		L2PcInstance player = getClient().getActiveChar();
		
		if (player == null)
			return;
		
		if (!getClient().getFloodProtectors().getTransaction().tryPerformAction("buy"))
		{
			player.sendMessage("You buying too fast.");
			return;
		}
		
		if (_items == null)
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		// Alt game - Karma punishment
		if (!Config.ALT_GAME_KARMA_PLAYER_CAN_SHOP && player.getKarma() > 0)
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		L2Object target = player.getTarget();
		L2Character merchant = null;
		if (!player.isGM())
		{
			if(target == null 
					|| (!player.isInsideRadius(target, INTERACTION_DISTANCE, true, false)) // Distance is too far)
					|| (player.getInstanceId() != target.getInstanceId()))
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			if((target instanceof L2MerchantInstance) || (target instanceof L2MerchantSummonInstance))
				merchant = (L2Character)target;
			else
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
		}
		
		double taxRate = 0;
		
		L2TradeList list = null;
		if (merchant != null)
		{
			List<L2TradeList> lists;
			if (merchant instanceof L2MerchantInstance)
			{
				lists = TradeController.getInstance().getBuyListByNpcId(((L2MerchantInstance) merchant).getNpcId());
				taxRate = ((L2MerchantInstance) merchant).getMpc().getTotalTaxRate();
			}
			else
			{
				lists = TradeController.getInstance().getBuyListByNpcId(((L2MerchantSummonInstance) merchant).getNpcId());
				taxRate = 50;
			}
			
			if (!player.isGM())
			{
				if (lists == null)
				{
					Util.handleIllegalPlayerAction(player, "Warning!! Character " + player.getName() + " of account " + player.getAccountName() + " sent a false BuyList list_id " + _listId, Config.DEFAULT_PUNISH);
					return;
				}
				for (L2TradeList tradeList : lists)
				{
					if (tradeList.getListId() == _listId)
						list = tradeList;
				}
			}
			else
				list = TradeController.getInstance().getBuyList(_listId);
		}
		else
			list = TradeController.getInstance().getBuyList(_listId);
		
		if (list == null)
		{
			Util.handleIllegalPlayerAction(player, "Warning!! Character " + player.getName() + " of account " + player.getAccountName() + " sent a false BuyList list_id " + _listId, Config.DEFAULT_PUNISH);
			return;
		}
		
		long totalPrice = 0;
		// Proceed the sell
		for (Item i : _items)
		{
			L2ItemInstance item = player.checkItemManipulation(i.getObjectId(), i.getCount(), "sell");
			if (item == null || (!item.isSellable()))
				continue;
			
			long price = item.getReferencePrice() / 2;
			totalPrice += price * i.getCount();
			if ((MAX_ADENA / i.getCount()) < price || totalPrice > MAX_ADENA)
			{
				Util.handleIllegalPlayerAction(player, "Warning!! Character " + player.getName() + " of account " + player.getAccountName() + " tried to purchase over " + MAX_ADENA + " adena worth of goods.", Config.DEFAULT_PUNISH);
				return;
			}
			
			if (Config.ALLOW_REFUND)
				item = player.getInventory().transferItem("Sell", i.getObjectId(), i.getCount(), player.getRefund(), player, merchant);
			else
				item = player.getInventory().destroyItem("Sell", i.getObjectId(), i.getCount(), player, merchant);
		}
		player.addAdena("Sell", totalPrice, merchant, false);
		
		// Update current load as well
		StatusUpdate su = new StatusUpdate(player);
		su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
		player.sendPacket(su);
		player.sendPacket(new ExBuySellListPacket(player, list, taxRate, true));
	}
	
	private static class Item
	{
		private final int _objectId;
		private final long _count;
		
		public Item(int objId, int id, long num)
		{
			_objectId = objId;
			_count = num;
		}
		
		public int getObjectId()
		{
			return _objectId;
		}
		
		public long getCount()
		{
			return _count;
		}
	}
	
	@Override
	public String getType()
	{
		return _C__37_REQUESTSELLITEM;
	}
}
