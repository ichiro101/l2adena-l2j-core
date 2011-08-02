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

import com.l2jserver.gameserver.ThreadPoolManager;
import com.l2jserver.gameserver.instancemanager.CastleManager;
import com.l2jserver.gameserver.instancemanager.ClanHallManager;
import com.l2jserver.gameserver.instancemanager.FortManager;
import com.l2jserver.gameserver.instancemanager.MapRegionManager;
import com.l2jserver.gameserver.instancemanager.TerritoryWarManager;
import com.l2jserver.gameserver.model.L2SiegeClan;
import com.l2jserver.gameserver.model.Location;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.actor.instance.L2SiegeFlagInstance;
import com.l2jserver.gameserver.model.entity.Castle;
import com.l2jserver.gameserver.model.entity.ClanHall;
import com.l2jserver.gameserver.model.entity.Fort;
import com.l2jserver.gameserver.model.entity.TvTEvent;

/**
 * This class ...
 *
 * @version $Revision: 1.7.2.3.2.6 $ $Date: 2005/03/27 15:29:30 $
 */
public final class RequestRestartPoint extends L2GameClientPacket
{
	private static final String _C__7D_REQUESTRESTARTPOINT = "[C] 7D RequestRestartPoint";
	private static Logger _log = Logger.getLogger(RequestRestartPoint.class.getName());
	
	protected int     _requestedPointType;
	protected boolean _continuation;
	
	@Override
	protected void readImpl()
	{
		_requestedPointType = readD();
	}
	
	class DeathTask implements Runnable
	{
		final L2PcInstance activeChar;
		
		DeathTask (L2PcInstance _activeChar)
		{
			activeChar = _activeChar;
		}
		
		@SuppressWarnings("synthetic-access")
		public void run()
		{
			Location loc = null;
			Castle castle = null;
			Fort fort = null;
			boolean isInDefense = false;
			int instanceId = 0;
			
			// force jail
			if (activeChar.isInJail())
			{
				_requestedPointType = 27;
			}
			else if (activeChar.isFestivalParticipant())
			{
				_requestedPointType = 5;
			}
			switch (_requestedPointType)
			{
				case 1: // to clanhall
					if (activeChar.getClan() == null || activeChar.getClan().getHasHideout() == 0)
					{
						_log.warning("Player ["+activeChar.getName()+"] called RestartPointPacket - To Clanhall and he doesn't have Clanhall!");
						return;
					}
					loc = MapRegionManager.getInstance().getTeleToLocation(activeChar, MapRegionManager.TeleportWhereType.ClanHall);
					
					if (ClanHallManager.getInstance().getClanHallByOwner(activeChar.getClan())!= null &&
							ClanHallManager.getInstance().getClanHallByOwner(activeChar.getClan()).getFunction(ClanHall.FUNC_RESTORE_EXP) != null)
					{
						activeChar.restoreExp(ClanHallManager.getInstance().getClanHallByOwner(activeChar.getClan()).getFunction(ClanHall.FUNC_RESTORE_EXP).getLvl());
					}
					break;
					
				case 2: // to castle
					castle = CastleManager.getInstance().getCastle(activeChar);
					
					if (castle != null && castle.getSiege().getIsInProgress())
					{
						// Siege in progress
						if (castle.getSiege().checkIsDefender(activeChar.getClan()))
							loc = MapRegionManager.getInstance().getTeleToLocation(activeChar, MapRegionManager.TeleportWhereType.Castle);
						// Just in case you lost castle while being dead.. Port to nearest Town.
						else if (castle.getSiege().checkIsAttacker(activeChar.getClan()))
							loc = MapRegionManager.getInstance().getTeleToLocation(activeChar, MapRegionManager.TeleportWhereType.Town);
						else
						{
							_log.warning("Player ["+activeChar.getName()+"] called RestartPointPacket - To Castle and he doesn't have Castle!");
							return;
						}
					}
					else
					{
						if (activeChar.getClan() == null || activeChar.getClan().getHasCastle() == 0)
							return;
						else
							loc = MapRegionManager.getInstance().getTeleToLocation(activeChar, MapRegionManager.TeleportWhereType.Castle);
					}
					if (CastleManager.getInstance().getCastleByOwner(activeChar.getClan())!= null &&
							CastleManager.getInstance().getCastleByOwner(activeChar.getClan()).getFunction(Castle.FUNC_RESTORE_EXP) != null)
					{
						activeChar.restoreExp(CastleManager.getInstance().getCastleByOwner(activeChar.getClan()).getFunction(Castle.FUNC_RESTORE_EXP).getLvl());
					}
					break;
					
				case 3: // to fortress
					//fort = FortManager.getInstance().getFort(activeChar);
					
					if ((activeChar.getClan() == null || activeChar.getClan().getHasFort() == 0) && !isInDefense)
					{
						_log.warning("Player ["+activeChar.getName()+"] called RestartPointPacket - To Fortress and he doesn't have Fortress!");
						return;
					}
					loc = MapRegionManager.getInstance().getTeleToLocation(activeChar, MapRegionManager.TeleportWhereType.Fortress);
					if (FortManager.getInstance().getFortByOwner(activeChar.getClan())!= null &&
							FortManager.getInstance().getFortByOwner(activeChar.getClan()).getFunction(Fort.FUNC_RESTORE_EXP) != null)
					{
						activeChar.restoreExp(FortManager.getInstance().getFortByOwner(activeChar.getClan()).getFunction(Fort.FUNC_RESTORE_EXP).getLvl());
					}
					break;
					
				case 4: // to siege HQ
					L2SiegeClan siegeClan = null;
					castle = CastleManager.getInstance().getCastle(activeChar);
					fort = FortManager.getInstance().getFort(activeChar);
					L2SiegeFlagInstance flag = TerritoryWarManager.getInstance().getFlagForClan(activeChar.getClan());
					
					if (castle != null && castle.getSiege().getIsInProgress())
						siegeClan = castle.getSiege().getAttackerClan(activeChar.getClan());
					else if (fort != null && fort.getSiege().getIsInProgress())
						siegeClan = fort.getSiege().getAttackerClan(activeChar.getClan());
					
					if ((siegeClan == null || siegeClan.getFlag().isEmpty()) && flag == null)
					{
						_log.warning("Player ["+activeChar.getName()+"] called RestartPointPacket - To Siege HQ and he doesn't have Siege HQ!");
						return;
					}
					loc = MapRegionManager.getInstance().getTeleToLocation(activeChar, MapRegionManager.TeleportWhereType.SiegeFlag);
					break;
					
				case 5: // Fixed or Player is a festival participant
					if (!activeChar.isGM() && !activeChar.isFestivalParticipant())
					{
						_log.warning("Player ["+activeChar.getName()+"] called RestartPointPacket - Fixed and he isn't festival participant!");
						return;
					}
					instanceId = activeChar.getInstanceId();
					loc = new Location(activeChar.getX(), activeChar.getY(), activeChar.getZ()); // spawn them where they died
					break;
				case 6: // TODO: agathion ress
					break;
				case 27: // to jail
					if (!activeChar.isInJail()) return;
					loc = new Location(-114356, -249645, -2984);
					break;
					
				default:
					loc = MapRegionManager.getInstance().getTeleToLocation(activeChar, MapRegionManager.TeleportWhereType.Town);
					break;
			}
			
			// Teleport and revive
			activeChar.setInstanceId(instanceId);
			activeChar.setIsIn7sDungeon(false);
			activeChar.setIsPendingRevive(true);
			activeChar.teleToLocation(loc, true);
		}
	}
	
	@Override
	protected void runImpl()
	{
		L2PcInstance activeChar = getClient().getActiveChar();
		
		if (activeChar == null)
			return;
		
		if (TvTEvent.isStarted() && TvTEvent.isPlayerParticipant(activeChar.getObjectId()))
			return;
		
		//SystemMessage sm2 = SystemMessage.getSystemMessage(SystemMessage.S1_S2);
		//sm2.addString("type:"+requestedPointType);
		//activeChar.sendPacket(sm2);
		
		if (activeChar.isFakeDeath())
		{
			activeChar.stopFakeDeath(true);
			return;
		}
		else if(!activeChar.isDead())
		{
			_log.warning("Living player ["+activeChar.getName()+"] called RestartPointPacket! Ban this player!");
			return;
		}
		
		Castle castle = CastleManager.getInstance().getCastle(activeChar.getX(), activeChar.getY(), activeChar.getZ());
		if (castle != null && castle.getSiege().getIsInProgress())
		{
			if (activeChar.getClan() != null && castle.getSiege().checkIsAttacker(activeChar.getClan()))
			{
				// Schedule respawn delay for attacker
				ThreadPoolManager.getInstance().scheduleGeneral(new DeathTask(activeChar), castle.getSiege().getAttackerRespawnDelay());
				if (castle.getSiege().getAttackerRespawnDelay() > 0)
					activeChar.sendMessage("You will be re-spawned in " + castle.getSiege().getAttackerRespawnDelay()/1000 + " seconds");
				return;
			}
		}
		
		// run immediately (no need to schedule)
		new DeathTask(activeChar).run();
	}
	
	@Override
	public String getType()
	{
		return _C__7D_REQUESTRESTARTPOINT;
	}
}
