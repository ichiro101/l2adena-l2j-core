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

import static com.l2jserver.gameserver.model.itemcontainer.PcInventory.MAX_ADENA;

import java.util.Arrays;
import java.util.List;

import com.l2jserver.Config;
import com.l2jserver.gameserver.RecipeController;
import com.l2jserver.gameserver.model.L2ManufactureItem;
import com.l2jserver.gameserver.model.L2ManufactureList;
import com.l2jserver.gameserver.model.L2RecipeList;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.ActionFailed;
import com.l2jserver.gameserver.network.serverpackets.RecipeShopMsg;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;
import com.l2jserver.gameserver.taskmanager.AttackStanceTaskManager;
import com.l2jserver.gameserver.util.Util;

/**
 * This class ...
 * cd(dd)
 * @version $Revision: 1.1.2.3.2.3 $ $Date: 2005/03/27 15:29:30 $
 */
public final class RequestRecipeShopListSet extends L2GameClientPacket
{
	private static final String _C__BB_RequestRecipeShopListSet = "[C] BB RequestRecipeShopListSet";
	//private static Logger _log = Logger.getLogger(RequestRecipeShopListSet.class.getName());
	
	private static final int BATCH_LENGTH = 12; // length of the one item
	
	private Recipe[] _items = null;
	
	@Override
	protected void readImpl()
	{
		int count = readD();
		if (count <= 0
				|| count > Config.MAX_ITEM_IN_PACKET
				|| count * BATCH_LENGTH != _buf.remaining())
		{
			return;
		}
		
		_items = new Recipe[count];
		for (int i = 0; i < count ; i++)
		{
			int id = readD();
			long cost = readQ();
			if (cost < 0)
			{
				_items = null;
				return;
			}
			_items[i] = new Recipe(id, cost);
		}
	}
	
	@Override
	protected void runImpl()
	{
		L2PcInstance player = getClient().getActiveChar();
		if (player == null)
			return;
		
		if (_items == null)
		{
			player.setPrivateStoreType(L2PcInstance.STORE_PRIVATE_NONE);
			player.broadcastUserInfo();
			return;
		}
		
		if (AttackStanceTaskManager.getInstance().getAttackStanceTask(player) || player.isInDuel())
		{
			player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANT_OPERATE_PRIVATE_STORE_DURING_COMBAT));
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (player.isInsideZone(L2Character.ZONE_NOSTORE))
		{
			player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NO_PRIVATE_WORKSHOP_HERE));
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		L2ManufactureList createList = new L2ManufactureList();
		
		List<L2RecipeList> dwarfRecipes = Arrays.asList(player.getDwarvenRecipeBook());
		List<L2RecipeList> commonRecipes = Arrays.asList(player.getCommonRecipeBook());
		
		for (Recipe i : _items)
		{
			L2RecipeList list = RecipeController.getInstance().getRecipeList(i.getRecipeId());
			
			if (!dwarfRecipes.contains(list) && !commonRecipes.contains(list))
			{
				Util.handleIllegalPlayerAction(player, "Warning!! Player " + player.getName() + " of account " + player.getAccountName()
						+ " tried to set recipe which he dont have.", Config.DEFAULT_PUNISH);
				return;
			}
			
			if (!i.addToList(createList))
			{
				Util.handleIllegalPlayerAction(player, "Warning!! Character "
						+ player.getName() + " of account "
						+ player.getAccountName() + " tried to set price more than "
						+ MAX_ADENA + " adena in Private Manufacture.",
						Config.DEFAULT_PUNISH);
				return;
			}
		}
		
		createList.setStoreName(player.getCreateList() != null ? player.getCreateList().getStoreName() : "");
		player.setCreateList(createList);
		
		player.setPrivateStoreType(L2PcInstance.STORE_PRIVATE_MANUFACTURE);
		player.sitDown();
		player.broadcastUserInfo();
		player.sendPacket(new RecipeShopMsg(player));
		player.broadcastPacket(new RecipeShopMsg(player));
	}
	
	private static class Recipe
	{
		private final int _recipeId;
		private final long _cost;
		
		public Recipe(int id, long c)
		{
			_recipeId = id;
			_cost = c;
		}
		
		public boolean addToList(L2ManufactureList list)
		{
			if (_cost > MAX_ADENA)
				return false;
			
			list.add(new L2ManufactureItem(_recipeId, _cost));
			return true;
		}
		
		public int getRecipeId()
		{
			return _recipeId;
		}
	}
	
	@Override
	public String getType()
	{
		return _C__BB_RequestRecipeShopListSet;
	}
}
