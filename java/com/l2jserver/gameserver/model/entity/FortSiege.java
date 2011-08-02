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
package com.l2jserver.gameserver.model.entity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import javolution.util.FastList;

import com.l2jserver.Config;
import com.l2jserver.L2DatabaseFactory;
import com.l2jserver.gameserver.ThreadPoolManager;
import com.l2jserver.gameserver.datatables.ClanTable;
import com.l2jserver.gameserver.datatables.NpcTable;
import com.l2jserver.gameserver.instancemanager.FortManager;
import com.l2jserver.gameserver.instancemanager.FortSiegeGuardManager;
import com.l2jserver.gameserver.instancemanager.FortSiegeManager;
import com.l2jserver.gameserver.instancemanager.FortSiegeManager.SiegeSpawn;
import com.l2jserver.gameserver.instancemanager.MapRegionManager;
import com.l2jserver.gameserver.model.CombatFlag;
import com.l2jserver.gameserver.model.L2Clan;
import com.l2jserver.gameserver.model.L2Object;
import com.l2jserver.gameserver.model.L2SiegeClan;
import com.l2jserver.gameserver.model.L2SiegeClan.SiegeClanType;
import com.l2jserver.gameserver.model.L2Spawn;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2DoorInstance;
import com.l2jserver.gameserver.model.actor.instance.L2FortCommanderInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.NpcSay;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;
import com.l2jserver.gameserver.templates.chars.L2NpcTemplate;

public class FortSiege implements Siegable
{
	protected static final Logger _log = Logger.getLogger(FortSiege.class.getName());
	
	public static enum TeleportWhoType
	{
		All, Attacker, Owner,
	}
	
	public class ScheduleEndSiegeTask implements Runnable
	{
		public void run()
		{
			if (!getIsInProgress())
				return;
			
			try
			{
				_siegeEnd = null;
				FortSiege.this.endSiege();
			}
			catch (Exception e)
			{
				_log.log(Level.WARNING, "Exception: ScheduleEndSiegeTask() for Fort: " + FortSiege.this._fort.getName() + " " + e.getMessage(), e);
			}
		}
	}
	
	public class ScheduleStartSiegeTask implements Runnable
	{
		private final Fort _fortInst;
		private final int _time;
		
		public ScheduleStartSiegeTask(int time)
		{
			_fortInst = FortSiege.this._fort;
			_time = time;
		}
		
		public void run()
		{
			if (getIsInProgress())
				return;
			
			try
			{
				final SystemMessage sm;
				if (_time == 3600) // 1hr remains
				{
					ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleStartSiegeTask(600), 3000000); // Prepare task for 10 minutes left.
				}
				else if (_time == 600) // 10min remains
				{
					getFort().despawnSuspiciousMerchant();
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_MINUTES_UNTIL_THE_FORTRESS_BATTLE_STARTS);
					sm.addNumber(10);
					announceToPlayer(sm);
					ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleStartSiegeTask(300), 300000); // Prepare task for 5 minutes left.
				}
				else if (_time == 300) // 5min remains
				{
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_MINUTES_UNTIL_THE_FORTRESS_BATTLE_STARTS);
					sm.addNumber(5);
					announceToPlayer(sm);
					ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleStartSiegeTask(60), 240000); // Prepare task for 1 minute left.
				}
				else if (_time == 60) // 1min remains
				{
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_MINUTES_UNTIL_THE_FORTRESS_BATTLE_STARTS);
					sm.addNumber(1);
					announceToPlayer(sm);
					ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleStartSiegeTask(30), 30000); // Prepare task for 30 seconds left.
				}
				else if (_time == 30) // 30seconds remains
				{
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_SECONDS_UNTIL_THE_FORTRESS_BATTLE_STARTS);
					sm.addNumber(30);
					announceToPlayer(sm);
					ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleStartSiegeTask(10), 20000); // Prepare task for 10 seconds left.
				}
				else if (_time == 10) // 10seconds remains
				{
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_SECONDS_UNTIL_THE_FORTRESS_BATTLE_STARTS);
					sm.addNumber(10);
					announceToPlayer(sm);
					ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleStartSiegeTask(5), 5000); // Prepare task for 5 seconds left.
				}
				else if (_time == 5) // 5seconds remains
				{
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_SECONDS_UNTIL_THE_FORTRESS_BATTLE_STARTS);
					sm.addNumber(5);
					announceToPlayer(sm);
					ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleStartSiegeTask(1), 4000); // Prepare task for 1 seconds left.
				}
				else if (_time == 1) // 1seconds remains
				{
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_SECONDS_UNTIL_THE_FORTRESS_BATTLE_STARTS);
					sm.addNumber(1);
					announceToPlayer(sm);
					ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleStartSiegeTask(0), 1000); // Prepare task start siege.
				}
				else if (_time == 0)// start siege
				{
					_fortInst.getSiege().startSiege();
				}
				else
					_log.warning("Exception: ScheduleStartSiegeTask(): unknown siege time: " + String.valueOf(_time));
			}
			catch (Exception e)
			{
				_log.log(Level.WARNING, "Exception: ScheduleStartSiegeTask() for Fort: " + _fortInst.getName() + " " + e.getMessage(), e);
			}
		}
	}
	
	public class ScheduleSuspiciousMerchantSpawn implements Runnable
	{
		public void run()
		{
			if (getIsInProgress())
				return;
			
			try
			{
				FortSiege.this._fort.spawnSuspiciousMerchant();
			}
			catch (Exception e)
			{
				_log.log(Level.WARNING, "Exception: ScheduleSuspicoiusMerchantSpawn() for Fort: " + FortSiege.this._fort.getName() + " " + e.getMessage(), e);
			}
		}
	}
	
	public class ScheduleSiegeRestore implements Runnable
	{
		public void run()
		{
			if (!getIsInProgress())
				return;
			
			try
			{
				_siegeRestore = null;
				FortSiege.this.resetSiege();
				announceToPlayer(SystemMessage.getSystemMessage(SystemMessageId.BARRACKS_FUNCTION_RESTORED));
			}
			catch (Exception e)
			{
				_log.log(Level.WARNING, "Exception: ScheduleSiegeRestore() for Fort: " + FortSiege.this._fort.getName() + " " + e.getMessage(), e);
			}
		}
	}
	
	private List<L2SiegeClan> _attackerClans = new FastList<L2SiegeClan>();
	
	// Fort setting
	protected FastList<L2Spawn> _commanders = new FastList<L2Spawn>();
	private final Fort _fort;
	private boolean _isInProgress = false;
	private FortSiegeGuardManager _siegeGuardManager;
	ScheduledFuture<?> _siegeEnd = null;
	ScheduledFuture<?> _siegeRestore = null;
	ScheduledFuture<?> _siegeStartTask = null;
	
	public FortSiege(Fort fort)
	{
		_fort = fort;
		
		checkAutoTask();
		FortSiegeManager.getInstance().addSiege(this);
	}
	
	/**
	 * When siege ends<BR><BR>
	 */
	public void endSiege()
	{
		if (getIsInProgress())
		{
			_isInProgress = false; // Flag so that siege instance can be started
			final SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.THE_FORTRESS_BATTLE_OF_S1_HAS_FINISHED);
			sm.addFortId(getFort().getFortId());
			announceToPlayer(sm);
			
			removeFlags(); // Removes all flags. Note: Remove flag before teleporting players
			unSpawnFlags();
			
			updatePlayerSiegeStateFlags(true);
			
			getFort().getZone().banishForeigners(getFort().getOwnerClan());
			getFort().getZone().setIsActive(false);
			getFort().getZone().updateZoneStatusForCharactersInside();
			getFort().getZone().setSiegeInstance(null);
			
			saveFortSiege(); // Save fort specific data
			clearSiegeClan(); // Clear siege clan from db
			removeCommanders(); // Remove commander from this fort
			
			getFort().spawnNpcCommanders(); // Spawn NPC commanders
			getSiegeGuardManager().unspawnSiegeGuard(); // Remove all spawned siege guard from this fort
			getFort().resetDoors(); // Respawn door to fort
			
			ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleSuspiciousMerchantSpawn(), FortSiegeManager.getInstance().getSuspiciousMerchantRespawnDelay() * 60 * 1000l); // Prepare 3hr task for suspicious merchant respawn
			setSiegeDateTime(true); // store suspicious merchant spawn in DB
			
			if (_siegeEnd != null)
			{
				_siegeEnd.cancel(true);
				_siegeEnd = null;
			}
			if (_siegeRestore != null)
			{
				_siegeRestore.cancel(true);
				_siegeRestore = null;
			}
			
			if (getFort().getOwnerClan() != null && getFort().getFlagPole().getMeshIndex() == 0)
				getFort().setVisibleFlag(true);
			
			_log.info("Siege of " + getFort().getName() + " fort finished.");
		}
	}
	
	/**
	 * When siege starts<BR><BR>
	 */
	public void startSiege()
	{
		if (!getIsInProgress())
		{
			if (_siegeStartTask != null) // used admin command "admin_startfortsiege"
			{
				_siegeStartTask.cancel(true);
				getFort().despawnSuspiciousMerchant();
			}
			_siegeStartTask = null;
			
			if (getAttackerClans().isEmpty())
				return;
			
			_isInProgress = true; // Flag so that same siege instance cannot be started again
			
			loadSiegeClan(); // Load siege clan from db
			updatePlayerSiegeStateFlags(false);
			teleportPlayer(FortSiege.TeleportWhoType.Attacker, MapRegionManager.TeleportWhereType.Town); // Teleport to the closest town
			
			getFort().despawnNpcCommanders(); // Despawn NPC commanders
			spawnCommanders(); // Spawn commanders
			getFort().resetDoors(); // Spawn door
			spawnSiegeGuard(); // Spawn siege guard
			getFort().setVisibleFlag(false);
			getFort().getZone().setSiegeInstance(this);
			getFort().getZone().setIsActive(true);
			getFort().getZone().updateZoneStatusForCharactersInside();
			
			// Schedule a task to prepare auto siege end
			_siegeEnd = ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleEndSiegeTask(), FortSiegeManager.getInstance().getSiegeLength() * 60 * 1000l); // Prepare auto end task
			
			final SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.THE_FORTRESS_BATTLE_S1_HAS_BEGUN);
			sm.addFortId(getFort().getFortId());
			announceToPlayer(sm);
			saveFortSiege();
			
			_log.info("Siege of " + getFort().getName() + " fort started.");
		}
	}
	
	/**
	 * Announce to player.<BR><BR>
	 * @param message The String of the message to send to player
	 */
	public void announceToPlayer(SystemMessage sm)
	{
		// announce messages only for participants
		L2Clan clan;
		for (L2SiegeClan siegeclan : getAttackerClans())
		{
			clan = ClanTable.getInstance().getClan(siegeclan.getClanId());
			for (L2PcInstance member : clan.getOnlineMembers(0))
			{
				if (member != null)
					member.sendPacket(sm);
			}
		}
		if (getFort().getOwnerClan() != null)
		{
			clan = ClanTable.getInstance().getClan(getFort().getOwnerClan().getClanId());
			for (L2PcInstance member : clan.getOnlineMembers(0))
			{
				if (member != null)
					member.sendPacket(sm);
			}
		}
	}
	
	public void announceToPlayer(SystemMessage sm, String s)
	{
		sm.addString(s);
		announceToPlayer(sm);
	}
	
	public void updatePlayerSiegeStateFlags(boolean clear)
	{
		L2Clan clan;
		for (L2SiegeClan siegeclan : getAttackerClans())
		{
			clan = ClanTable.getInstance().getClan(siegeclan.getClanId());
			for (L2PcInstance member : clan.getOnlineMembers(0))
			{
				if (member == null)
					continue;
				
				if (clear)
				{
					member.setSiegeState((byte) 0);
					member.setSiegeSide(0);
					member.setIsInSiege(false);
					member.stopFameTask();
				}
				else
				{
					member.setSiegeState((byte) 1);
					member.setSiegeSide(getFort().getFortId());
					if (checkIfInZone(member))
					{
						member.setIsInSiege(true);
						member.startFameTask(Config.FORTRESS_ZONE_FAME_TASK_FREQUENCY * 1000, Config.FORTRESS_ZONE_FAME_AQUIRE_POINTS);
					}
				}
				member.broadcastUserInfo();
			}
		}
		if (getFort().getOwnerClan() != null)
		{
			clan = ClanTable.getInstance().getClan(getFort().getOwnerClan().getClanId());
			for (L2PcInstance member : clan.getOnlineMembers(0))
			{
				if (member == null)
					continue;
				
				if (clear)
				{
					member.setSiegeState((byte) 0);
					member.setSiegeSide(0);
					member.setIsInSiege(false);
					member.stopFameTask();
				}
				else
				{
					member.setSiegeState((byte) 2);
					member.setSiegeSide(getFort().getFortId());
					if (checkIfInZone(member))
					{
						member.setIsInSiege(true);
						member.startFameTask(Config.FORTRESS_ZONE_FAME_TASK_FREQUENCY * 1000, Config.FORTRESS_ZONE_FAME_AQUIRE_POINTS);
					}
				}
				member.broadcastUserInfo();
			}
		}
	}
	
	/** Return true if object is inside the zone */
	public boolean checkIfInZone(L2Object object)
	{
		return checkIfInZone(object.getX(), object.getY(), object.getZ());
	}
	
	/** Return true if object is inside the zone */
	public boolean checkIfInZone(int x, int y, int z)
	{
		return (getIsInProgress() && (getFort().checkIfInZone(x, y, z))); // Fort zone during siege
	}
	
	/**
	 * Return true if clan is attacker<BR><BR>
	 * @param clan The L2Clan of the player
	 */
	public boolean checkIsAttacker(L2Clan clan)
	{
		return (getAttackerClan(clan) != null);
	}
	
	/**
	 * Return true if clan is defender<BR><BR>
	 * @param clan The L2Clan of the player
	 */
	public boolean checkIsDefender(L2Clan clan)
	{
		if (clan != null && getFort().getOwnerClan() == clan)
			return true;
		
		return false;
	}
	
	/** Clear all registered siege clans from database for fort */
	public void clearSiegeClan()
	{
		Connection con = null;
		PreparedStatement statement = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("DELETE FROM fortsiege_clans WHERE fort_id=?");
			statement.setInt(1, getFort().getFortId());
			statement.execute();
			statement.close();
			
			if (getFort().getOwnerClan() != null)
			{
				statement = con.prepareStatement("DELETE FROM fortsiege_clans WHERE clan_id=?");
				statement.setInt(1, getFort().getOwnerClan().getClanId());
				statement.execute();
			}
			
			getAttackerClans().clear();
			
			// if siege is in progress, end siege
			if (getIsInProgress())
				endSiege();
			
			// if siege isnt in progress (1hr waiting time till siege starts), cancel waiting time
			if (_siegeStartTask != null)
			{
				_siegeStartTask.cancel(true);
				_siegeStartTask = null;
			}
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Exception: clearSiegeClan(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/** Set the date for the next siege. */
	private void clearSiegeDate()
	{
		getFort().getSiegeDate().setTimeInMillis(0);
	}
	
	/** Return list of L2PcInstance registered as attacker in the zone. */
	public List<L2PcInstance> getAttackersInZone()
	{
		List<L2PcInstance> players = new FastList<L2PcInstance>();
		L2Clan clan;
		for (L2SiegeClan siegeclan : getAttackerClans())
		{
			clan = ClanTable.getInstance().getClan(siegeclan.getClanId());
			for (L2PcInstance player : clan.getOnlineMembers(0))
			{
				if (player == null)
					continue;
				
				if (player.isInSiege())
					players.add(player);
			}
		}
		return players;
	}
	
	/** Return list of L2PcInstance in the zone. */
	public List<L2PcInstance> getPlayersInZone()
	{
		return getFort().getZone().getAllPlayers();
	}
	
	/** Return list of L2PcInstance owning the fort in the zone. */
	public List<L2PcInstance> getOwnersInZone()
	{
		List<L2PcInstance> players = new FastList<L2PcInstance>();
		L2Clan clan;
		if (getFort().getOwnerClan() != null)
		{
			clan = ClanTable.getInstance().getClan(getFort().getOwnerClan().getClanId());
			if (clan != getFort().getOwnerClan())
				return null;
			
			for (L2PcInstance player : clan.getOnlineMembers(0))
			{
				if (player == null)
					continue;
				
				if (player.isInSiege())
					players.add(player);
			}
		}
		return players;
	}
	
	/** Commander was killed */
	public void killedCommander(L2FortCommanderInstance instance)
	{
		if (_commanders != null && getFort() != null && _commanders.size() != 0)
		{
			L2Spawn spawn = instance.getSpawn();
			if (spawn != null)
			{
				FastList<SiegeSpawn> commanders = FortSiegeManager.getInstance().getCommanderSpawnList(getFort().getFortId());
				for (SiegeSpawn spawn2 : commanders)
				{
					if (spawn2.getNpcId() == spawn.getNpcid())
					{
						int message = 0;
						switch (spawn2.getId())
						{
							case 1:
								message = 1300004; // You may have broken our arrows, but you will never break our will! Archers, retreat!
								break;
							case 2:
								message = 1300006; // Aiieeee! Command Center! This is guard unit! We need backup right away!
								break;
							case 3:
								message = 1300005; // At last! The Magic Field that protects the fortress has weakened! Volunteers, stand back!
								break;
							case 4:
								message = 1300020; // I feel so much grief that I can't even take care of myself. There isn't any reason for me to stay here any longer.
								break;
						}
						if (message != 0)
							instance.broadcastPacket(new NpcSay(instance.getObjectId(), 1, instance.getNpcId(), message));
					}
				}
				_commanders.remove(spawn);
				if (_commanders.isEmpty())
				{
					// spawn fort flags
					spawnFlag(getFort().getFortId());
					// cancel door/commanders respawn
					if (_siegeRestore != null)
					{
						_siegeRestore.cancel(true);
					}
					// open doors in main building
					for (L2DoorInstance door : getFort().getDoors())
					{
						if (!door.getIsCommanderDoor())
							continue;
						
						door.openMe();
					}
					getFort().getSiege().announceToPlayer(SystemMessage.getSystemMessage(SystemMessageId.ALL_BARRACKS_OCCUPIED));
				}
				// schedule restoring doors/commanders respawn
				else if (_siegeRestore == null)
				{
					getFort().getSiege().announceToPlayer(SystemMessage.getSystemMessage(SystemMessageId.SEIZED_BARRACKS));
					_siegeRestore = ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleSiegeRestore(), FortSiegeManager.getInstance().getCountDownLength() * 60 * 1000l);
				}
				else
					getFort().getSiege().announceToPlayer(SystemMessage.getSystemMessage(SystemMessageId.SEIZED_BARRACKS));
			}
			else
				_log.warning("FortSiege.killedCommander(): killed commander, but commander not registered for fortress. NpcId: " + instance.getNpcId() + " FortId: " + getFort().getFortId());
		}
	}
	
	/** Remove the flag that was killed */
	public void killedFlag(L2Npc flag)
	{
		if (flag == null)
			return;
		
		for (L2SiegeClan clan : getAttackerClans())
		{
			if (clan.removeFlag(flag))
				return;
		}
	}
	
	/**
	 * Register clan as attacker<BR><BR>
	 * @param player The L2PcInstance of the player trying to register
	 */
	public boolean registerAttacker(L2PcInstance player, boolean force)
	{
		if (player.getClan() == null)
			return false;
		
		if (force || checkIfCanRegister(player))
		{
			saveSiegeClan(player.getClan()); // Save to database
			// if the first registering we start the timer
			if (getAttackerClans().size() == 1)
			{
				if (!force)
					player.reduceAdena("siege", 250000, null, true);
				startAutoTask(true);
			}
			return true;
		}
		return false;
	}
	
	/**
	 * Remove clan from siege<BR><BR>
	 * This function does not do any checks and should not be called from bypass !
	 * @param clanId The int of player's clan id
	 */
	private void removeSiegeClan(int clanId)
	{
		Connection con = null;
		PreparedStatement statement = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			if (clanId != 0)
				statement = con.prepareStatement("DELETE FROM fortsiege_clans WHERE fort_id=? AND clan_id=?");
			else
				statement = con.prepareStatement("DELETE FROM fortsiege_clans WHERE fort_id=?");
			
			statement.setInt(1, getFort().getFortId());
			if (clanId != 0)
				statement.setInt(2, clanId);
			statement.execute();
			
			loadSiegeClan();
			if (getAttackerClans().isEmpty())
			{
				if (getIsInProgress())
					endSiege();
				else
					saveFortSiege(); // Clear siege time in DB
				
				if (_siegeStartTask != null)
				{
					_siegeStartTask.cancel(true);
					_siegeStartTask = null;
				}
			}
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Exception on removeSiegeClan: " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Remove clan from siege<BR><BR>
	 * @paramclan The clan being removed
	 */
	public void removeSiegeClan(L2Clan clan)
	{
		if (clan == null || clan.getHasFort() == getFort().getFortId() || !FortSiegeManager.getInstance().checkIsRegistered(clan, getFort().getFortId()))
			return;
		
		removeSiegeClan(clan.getClanId());
	}
	
	/**
	 * Start the auto tasks<BR>
	 * <BR>
	 */
	public void checkAutoTask()
	{
		if (_siegeStartTask != null) //safety check
			return;
		
		final long delay = getFort().getSiegeDate().getTimeInMillis() - Calendar.getInstance().getTimeInMillis();
		
		if (delay < 0)
		{
			// siege time in past
			saveFortSiege();
			clearSiegeClan(); // remove all clans
			// spawn suspicious merchant immediately
			ThreadPoolManager.getInstance().executeTask(new ScheduleSuspiciousMerchantSpawn());
			return;
		}
		else
		{
			loadSiegeClan();
			if (getAttackerClans().isEmpty())
				// no attackers - waiting for suspicious merchant spawn
				ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleSuspiciousMerchantSpawn(), delay);
			else
			{
				// preparing start siege task
				if (delay > 3600000) // more than hour, how this can happens ? spawn suspicious merchant
				{
					ThreadPoolManager.getInstance().executeTask(new ScheduleSuspiciousMerchantSpawn());
					_siegeStartTask = ThreadPoolManager.getInstance().scheduleGeneral(new FortSiege.ScheduleStartSiegeTask(3600), delay - 3600000);
				}
				if (delay > 600000) // more than 10 min, spawn suspicious merchant
				{
					ThreadPoolManager.getInstance().executeTask(new ScheduleSuspiciousMerchantSpawn());
					_siegeStartTask = ThreadPoolManager.getInstance().scheduleGeneral(new FortSiege.ScheduleStartSiegeTask(600), delay - 600000);
				}
				else if (delay > 300000) // more than 5 min
					_siegeStartTask = ThreadPoolManager.getInstance().scheduleGeneral(new FortSiege.ScheduleStartSiegeTask(300), delay - 300000);
				else if (delay > 60000) //more than 1 min
					_siegeStartTask = ThreadPoolManager.getInstance().scheduleGeneral(new FortSiege.ScheduleStartSiegeTask(60), delay - 60000);
				else
					// lower than 1 min, set to 1 min
					_siegeStartTask = ThreadPoolManager.getInstance().scheduleGeneral(new FortSiege.ScheduleStartSiegeTask(60), 0);
				
				_log.info("Siege of " + getFort().getName() + " fort: " + getFort().getSiegeDate().getTime());
			}
		}
	}
	
	/**
	 * Start the auto tasks<BR><BR>
	 */
	public void startAutoTask(boolean setTime)
	{
		if (_siegeStartTask != null)
			return;
		
		if (setTime)
			setSiegeDateTime(false);
		
		if (getFort().getOwnerClan() != null)
			getFort().getOwnerClan().broadcastToOnlineMembers(SystemMessage.getSystemMessage(SystemMessageId.A_FORTRESS_IS_UNDER_ATTACK));
		
		// Execute siege auto start
		_siegeStartTask = ThreadPoolManager.getInstance().scheduleGeneral(new FortSiege.ScheduleStartSiegeTask(3600), 0);
	}
	
	/**
	 * Teleport players
	 */
	public void teleportPlayer(TeleportWhoType teleportWho, MapRegionManager.TeleportWhereType teleportWhere)
	{
		List<L2PcInstance> players;
		switch (teleportWho)
		{
			case Owner:
				players = getOwnersInZone();
				break;
			case Attacker:
				players = getAttackersInZone();
				break;
			default:
				players = getPlayersInZone();
		}
		
		for (L2PcInstance player : players)
		{
			if (player.isGM() || player.isInJail())
				continue;
			
			player.teleToLocation(teleportWhere);
		}
	}
	
	// =========================================================
	// Method - Private
	/**
	 * Add clan as attacker<BR><BR>
	 * @param clanId The int of clan's id
	 */
	private void addAttacker(int clanId)
	{
		getAttackerClans().add(new L2SiegeClan(clanId, SiegeClanType.ATTACKER)); // Add registered attacker to attacker list
	}
	
	/**
	 * Return true if the player can register.<BR><BR>
	 * @param player The L2PcInstance of the player trying to register
	 */
	public boolean checkIfCanRegister(L2PcInstance player)
	{
		boolean b = true;
		if (player.getClan() == null || player.getClan().getLevel() < FortSiegeManager.getInstance().getSiegeClanMinLevel())
		{
			b = false;
			player.sendMessage("Only clans with Level " + FortSiegeManager.getInstance().getSiegeClanMinLevel() + " and higher may register for a fortress siege.");
		}
		else if ((player.getClanPrivileges() & L2Clan.CP_CS_MANAGE_SIEGE) != L2Clan.CP_CS_MANAGE_SIEGE)
		{
			b = false;
			player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT));
		}
		else if (player.getClan() == getFort().getOwnerClan())
		{
			b = false;
			player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CLAN_THAT_OWNS_CASTLE_IS_AUTOMATICALLY_REGISTERED_DEFENDING));
		}
		else if (getFort().getOwnerClan() != null && player.getClan().getHasCastle() > 0 && player.getClan().getHasCastle() == getFort().getCastleId())
		{
			b = false;
			player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANT_REGISTER_TO_SIEGE_DUE_TO_CONTRACT));
		}
		else if (getFort().getTimeTillRebelArmy() > 0 && getFort().getTimeTillRebelArmy() <= 7200)
		{
			b = false;
			player.sendMessage("You cannot register for the fortress siege 2 hours prior to rebel army attack.");
		}
		else if (getFort().getSiege().getAttackerClans().isEmpty() && player.getInventory().getAdena() < 250000)
		{
			b = false;
			player.sendMessage("You need 250,000 adena to register"); // replace me with html
		}
		else
		{
			for (Fort fort : FortManager.getInstance().getForts())
			{
				if (fort.getSiege().getAttackerClan(player.getClanId()) != null)
				{
					b = false;
					player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.ALREADY_REQUESTED_SIEGE_BATTLE));
					break;
				}
				if (fort.getOwnerClan() == player.getClan() && (fort.getSiege().getIsInProgress() || fort.getSiege()._siegeStartTask != null))
				{
					b = false;
					player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.ALREADY_REQUESTED_SIEGE_BATTLE));
					break;
				}
			}
		}
		return b;
	}
	
	/**
	 * Return true if the clan has already registered to a siege for the same day.<BR><BR>
	 * @param clan The L2Clan of the player trying to register
	 */
	public boolean checkIfAlreadyRegisteredForSameDay(L2Clan clan)
	{
		for (FortSiege siege : FortSiegeManager.getInstance().getSieges())
		{
			if (siege == this)
				continue;
			
			if (siege.getSiegeDate().get(Calendar.DAY_OF_WEEK) == getSiegeDate().get(Calendar.DAY_OF_WEEK))
			{
				if (siege.checkIsAttacker(clan))
					return true;
				if (siege.checkIsDefender(clan))
					return true;
			}
		}
		
		return false;
	}
	
	private void setSiegeDateTime(boolean merchant)
	{
		Calendar newDate = Calendar.getInstance();
		if (merchant)
			newDate.add(Calendar.MINUTE, FortSiegeManager.getInstance().getSuspiciousMerchantRespawnDelay());
		else
			newDate.add(Calendar.MINUTE, 60);
		getFort().setSiegeDate(newDate);
		saveSiegeDate();
	}
	
	/** Load siege clans. */
	private void loadSiegeClan()
	{
		Connection con = null;
		PreparedStatement statement = null;
		try
		{
			getAttackerClans().clear();
			
			ResultSet rs = null;
			
			con = L2DatabaseFactory.getInstance().getConnection();
			
			statement = con.prepareStatement("SELECT clan_id FROM fortsiege_clans WHERE fort_id=?");
			statement.setInt(1, getFort().getFortId());
			rs = statement.executeQuery();
			
			while (rs.next())
			{
				addAttacker(rs.getInt("clan_id"));
			}
			
			rs.close();
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Exception: loadSiegeClan(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/** Remove commanders. */
	private void removeCommanders()
	{
		if (_commanders != null && !_commanders.isEmpty())
		{
			// Remove all instance of commanders for this fort
			for (L2Spawn spawn : _commanders)
			{
				if (spawn != null)
				{
					spawn.stopRespawn();
					if (spawn.getLastSpawn() != null)
						spawn.getLastSpawn().deleteMe();
				}
			}
			_commanders.clear();
		}
	}
	
	/** Remove all flags. */
	private void removeFlags()
	{
		for (L2SiegeClan sc : getAttackerClans())
		{
			if (sc != null)
				sc.removeFlags();
		}
	}
	
	/** Save fort siege related to database. */
	private void saveFortSiege()
	{
		clearSiegeDate(); // clear siege date
		saveSiegeDate(); // Save the new date
	}
	
	/** Save siege date to database. */
	private void saveSiegeDate()
	{
		Connection con = null;
		PreparedStatement statement = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("UPDATE fort SET siegeDate = ? WHERE id = ?");
			statement.setLong(1, getSiegeDate().getTimeInMillis());
			statement.setInt(2, getFort().getFortId());
			statement.execute();
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Exception: saveSiegeDate(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Save registration to database.<BR><BR>
	 * @param clan The L2Clan of player
	 * @param typeId -1 = owner 0 = defender, 1 = attacker, 2 = defender waiting
	 */
	private void saveSiegeClan(L2Clan clan)
	{
		if (getAttackerClans().size() >= FortSiegeManager.getInstance().getAttackerMaxClans())
			return;
		
		Connection con = null;
		PreparedStatement statement = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("INSERT INTO fortsiege_clans (clan_id,fort_id) values (?,?)");
			statement.setInt(1, clan.getClanId());
			statement.setInt(2, getFort().getFortId());
			statement.execute();
			
			addAttacker(clan.getClanId());
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Exception: saveSiegeClan(L2Clan clan): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/** Spawn commanders. */
	private void spawnCommanders()
	{
		//Set commanders array size if one does not exist
		try
		{
			_commanders.clear();
			L2Spawn spawnDat;
			L2NpcTemplate template1;
			for (SiegeSpawn _sp : FortSiegeManager.getInstance().getCommanderSpawnList(getFort().getFortId()))
			{
				template1 = NpcTable.getInstance().getTemplate(_sp.getNpcId());
				if (template1 != null)
				{
					spawnDat = new L2Spawn(template1);
					spawnDat.setAmount(1);
					spawnDat.setLocx(_sp.getLocation().getX());
					spawnDat.setLocy(_sp.getLocation().getY());
					spawnDat.setLocz(_sp.getLocation().getZ());
					spawnDat.setHeading(_sp.getLocation().getHeading());
					spawnDat.setRespawnDelay(60);
					spawnDat.doSpawn();
					spawnDat.stopRespawn();
					_commanders.add(spawnDat);
				}
				else
				{
					_log.warning("FortSiege.spawnCommander: Data missing in NPC table for ID: " + _sp.getNpcId() + ".");
				}
			}
		}
		catch (Exception e)
		{
			// problem with initializing spawn, go to next one
			_log.log(Level.WARNING, "FortSiege.spawnCommander: Spawn could not be initialized: " + e.getMessage(), e);
		}
	}
	
	private void spawnFlag(int Id)
	{
		for (CombatFlag cf : FortSiegeManager.getInstance().getFlagList(Id))
			cf.spawnMe();
	}
	
	private void unSpawnFlags()
	{
		if (FortSiegeManager.getInstance().getFlagList(getFort().getFortId()) == null)
			return;
		
		for (CombatFlag cf : FortSiegeManager.getInstance().getFlagList(getFort().getFortId()))
			cf.unSpawnMe();
	}
	
	/**
	 * Spawn siege guard.<BR><BR>
	 */
	private void spawnSiegeGuard()
	{
		getSiegeGuardManager().spawnSiegeGuard();
	}
	
	public final L2SiegeClan getAttackerClan(L2Clan clan)
	{
		if (clan == null)
			return null;
		
		return getAttackerClan(clan.getClanId());
	}
	
	public final L2SiegeClan getAttackerClan(int clanId)
	{
		for (L2SiegeClan sc : getAttackerClans())
			if (sc != null && sc.getClanId() == clanId)
				return sc;
		
		return null;
	}
	
	public final List<L2SiegeClan> getAttackerClans()
	{
		return _attackerClans;
	}
	
	public final Fort getFort()
	{
		return _fort;
	}
	
	public final boolean getIsInProgress()
	{
		return _isInProgress;
	}
	
	public final Calendar getSiegeDate()
	{
		return getFort().getSiegeDate();
	}
	
	public List<L2Npc> getFlag(L2Clan clan)
	{
		if (clan != null)
		{
			L2SiegeClan sc = getAttackerClan(clan);
			if (sc != null)
				return sc.getFlag();
		}
		
		return null;
	}
	
	public final FortSiegeGuardManager getSiegeGuardManager()
	{
		if (_siegeGuardManager == null)
			_siegeGuardManager = new FortSiegeGuardManager(getFort());
		
		return _siegeGuardManager;
	}
	
	public void resetSiege()
	{
		// reload commanders and repair doors
		removeCommanders();
		spawnCommanders();
		getFort().resetDoors();
	}
	
	public List<L2Spawn> getCommanders()
	{
		return _commanders;
	}
	
	@Override
	public L2SiegeClan getDefenderClan(int clanId)
	{
		return null;
	}
	
	@Override
	public L2SiegeClan getDefenderClan(L2Clan clan)
	{
		return null;
	}
	
	@Override
	public List<L2SiegeClan> getDefenderClans()
	{
		return null;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.model.entity.Siegable#giveFame()
	 */
	@Override
	public boolean giveFame()
	{
		return true;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.model.entity.Siegable#getFameFrequency()
	 */
	@Override
	public int getFameFrequency()
	{
		return Config.FORTRESS_ZONE_FAME_TASK_FREQUENCY;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.model.entity.Siegable#getFameAmount()
	 */
	@Override
	public int getFameAmount()
	{
		return Config.FORTRESS_ZONE_FAME_AQUIRE_POINTS;
	}
}
