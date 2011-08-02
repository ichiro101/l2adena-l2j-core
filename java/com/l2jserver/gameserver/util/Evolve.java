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
package com.l2jserver.gameserver.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.l2jserver.Config;
import com.l2jserver.L2DatabaseFactory;
import com.l2jserver.gameserver.ThreadPoolManager;
import com.l2jserver.gameserver.datatables.NpcTable;
import com.l2jserver.gameserver.datatables.SummonItemsData;
import com.l2jserver.gameserver.model.L2ItemInstance;
import com.l2jserver.gameserver.model.L2SummonItem;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.L2Summon;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PetInstance;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.InventoryUpdate;
import com.l2jserver.gameserver.network.serverpackets.MagicSkillLaunched;
import com.l2jserver.gameserver.network.serverpackets.MagicSkillUse;
import com.l2jserver.gameserver.network.serverpackets.StatusUpdate;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;
import com.l2jserver.gameserver.templates.chars.L2NpcTemplate;

public final class Evolve
{
	public static final Logger _log = Logger.getLogger(Evolve.class.getName());
	
	public static final boolean doEvolve(L2PcInstance player, L2Npc npc, int itemIdtake, int itemIdgive, int petminlvl)
	{
		if (itemIdtake == 0 || itemIdgive == 0 || petminlvl == 0)
			return false;
		
		L2Summon summon = player.getPet();
		
		if (summon == null || !(summon instanceof L2PetInstance))
			return false;
		
		L2PetInstance currentPet = (L2PetInstance)summon;
		
		if (currentPet.isAlikeDead())
		{
			Util.handleIllegalPlayerAction(player, "Player " + player.getName() + " tried to use death pet exploit!", Config.DEFAULT_PUNISH);
			return false;
		}
		
		L2ItemInstance item = null;
		long petexp = currentPet.getStat().getExp();
		String oldname = currentPet.getName();
		int oldX = currentPet.getX();
		int oldY = currentPet.getY();
		int oldZ = currentPet.getZ();
		
		L2SummonItem olditem = SummonItemsData.getInstance().getSummonItem(itemIdtake);
		
		if (olditem == null)
			return false;
		
		int oldnpcID = olditem.getNpcId();
		
		if (currentPet.getStat().getLevel() < petminlvl || currentPet.getNpcId() != oldnpcID)
			return false;
		
		L2SummonItem sitem = SummonItemsData.getInstance().getSummonItem(itemIdgive);
		
		if (sitem == null)
			return false;
		
		int npcID = sitem.getNpcId();
		
		if (npcID == 0)
			return false;
		
		L2NpcTemplate npcTemplate = NpcTable.getInstance().getTemplate(npcID);
		
		currentPet.unSummon(player);
		
		//deleting old pet item
		currentPet.destroyControlItem(player, true);
		
		item = player.getInventory().addItem("Evolve", itemIdgive, 1, player, npc);
		
		//Summoning new pet
		L2PetInstance petSummon = L2PetInstance.spawnPet(npcTemplate, player, item);
		
		if (petSummon == null)
			return false;
		
		// Fix for non-linear baby pet exp
		long _minimumexp = petSummon.getStat().getExpForLevel(petminlvl);
		if (petexp < _minimumexp)
			petexp = _minimumexp;
		
		petSummon.getStat().addExp(petexp);
		petSummon.setCurrentHp(petSummon.getMaxHp());
		petSummon.setCurrentMp(petSummon.getMaxMp());
		petSummon.setCurrentFed(petSummon.getMaxFed());
		petSummon.setTitle(player.getName());
		petSummon.setName(oldname);
		petSummon.setRunning();
		petSummon.store();
		
		player.setPet(petSummon);
		
		player.sendPacket(new MagicSkillUse(npc, 2046, 1, 1000, 600000));
		player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.SUMMON_A_PET));
		//L2World.getInstance().storeObject(petSummon);
		petSummon.spawnMe(oldX, oldY, oldZ);
		petSummon.startFeed();
		item.setEnchantLevel(petSummon.getLevel());
		
		ThreadPoolManager.getInstance().scheduleGeneral(new EvolveFinalizer(player, petSummon), 900);
		
		if (petSummon.getCurrentFed() <= 0)
			ThreadPoolManager.getInstance().scheduleGeneral(new EvolveFeedWait(player, petSummon), 60000);
		else
			petSummon.startFeed();
		
		return true;
	}
	
	public static final boolean doRestore(L2PcInstance player, L2Npc npc, int itemIdtake, int itemIdgive, int petminlvl)
	{
		if (itemIdtake == 0 || itemIdgive == 0 || petminlvl == 0)
			return false;
		
		L2ItemInstance item = player.getInventory().getItemByItemId(itemIdtake);
		if (item == null)
			return false;
		
		int oldpetlvl = item.getEnchantLevel();
		if (oldpetlvl < petminlvl)
			oldpetlvl = petminlvl;
		
		L2SummonItem oldItem = SummonItemsData.getInstance().getSummonItem(itemIdtake);
		if (oldItem == null)
			return false;
		
		L2SummonItem sItem = SummonItemsData.getInstance().getSummonItem(itemIdgive);
		if (sItem == null)
			return false;
		
		int npcId = sItem.getNpcId();
		if (npcId == 0)
			return false;
		
		L2NpcTemplate npcTemplate = NpcTable.getInstance().getTemplate(npcId);
		
		//deleting old pet item
		L2ItemInstance removedItem = player.getInventory().destroyItem("PetRestore", item, player, npc);
		player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_DISAPPEARED).addItemName(removedItem));
		
		//Give new pet item
		L2ItemInstance addedItem = player.getInventory().addItem("PetRestore", itemIdgive, 1, player, npc);
		
		//Summoning new pet
		L2PetInstance petSummon = L2PetInstance.spawnPet(npcTemplate, player, addedItem);
		if (petSummon == null)
			return false;
		
		long _maxexp = petSummon.getStat().getExpForLevel(oldpetlvl);
		
		petSummon.getStat().addExp(_maxexp);
		petSummon.setCurrentHp(petSummon.getMaxHp());
		petSummon.setCurrentMp(petSummon.getMaxMp());
		petSummon.setCurrentFed(petSummon.getMaxFed());
		petSummon.setTitle(player.getName());
		petSummon.setRunning();
		petSummon.store();
		
		player.setPet(petSummon);
		
		player.sendPacket(new MagicSkillUse(npc, 2046, 1, 1000, 600000));
		player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.SUMMON_A_PET));
		//L2World.getInstance().storeObject(petSummon);
		petSummon.spawnMe(player.getX(), player.getY(), player.getZ());
		petSummon.startFeed();
		addedItem.setEnchantLevel(petSummon.getLevel());
		
		//Inventory update
		InventoryUpdate iu = new InventoryUpdate();
		iu.addRemovedItem(removedItem);
		player.sendPacket(iu);
		
		StatusUpdate su = new StatusUpdate(player);
		su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
		player.sendPacket(su);
		
		player.broadcastUserInfo();
		
		L2World world = L2World.getInstance();
		world.removeObject(removedItem);
		
		ThreadPoolManager.getInstance().scheduleGeneral(new EvolveFinalizer(player, petSummon), 900);
		
		if (petSummon.getCurrentFed() <= 0)
			ThreadPoolManager.getInstance().scheduleGeneral(new EvolveFeedWait(player, petSummon), 60000);
		else
			petSummon.startFeed();
		
		// pet control item no longer exists, delete the pet from the db
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("DELETE FROM pets WHERE item_obj_id=?");
			statement.setInt(1, removedItem.getObjectId());
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		return true;
	}
	
	static final class EvolveFeedWait implements Runnable
	{
		private final L2PcInstance _activeChar;
		private final L2PetInstance _petSummon;
		
		EvolveFeedWait(L2PcInstance activeChar, L2PetInstance petSummon)
		{
			_activeChar = activeChar;
			_petSummon = petSummon;
		}
		
		public void run()
		{
			try
			{
				if (_petSummon.getCurrentFed() <= 0)
					_petSummon.unSummon(_activeChar);
				else
					_petSummon.startFeed();
			}
			catch (Exception e)
			{
				_log.log(Level.WARNING, "", e);
			}
		}
	}
	
	static final class EvolveFinalizer implements Runnable
	{
		private final L2PcInstance _activeChar;
		private final L2PetInstance _petSummon;
		
		EvolveFinalizer(L2PcInstance activeChar, L2PetInstance petSummon)
		{
			_activeChar = activeChar;
			_petSummon = petSummon;
		}
		
		public void run()
		{
			try
			{
				_activeChar.sendPacket(new MagicSkillLaunched(_activeChar, 2046, 1));
				_petSummon.setFollowStatus(true);
				_petSummon.setShowSummonAnimation(false);
			}
			catch (Throwable e)
			{
				_log.log(Level.WARNING, "", e);
			}
		}
	}
}