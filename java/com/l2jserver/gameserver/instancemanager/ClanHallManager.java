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
package com.l2jserver.gameserver.instancemanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javolution.util.FastMap;

import com.l2jserver.L2DatabaseFactory;
import com.l2jserver.gameserver.datatables.ClanTable;
import com.l2jserver.gameserver.model.L2Clan;
import com.l2jserver.gameserver.model.L2Object;
import com.l2jserver.gameserver.model.entity.Auction;
import com.l2jserver.gameserver.model.entity.ClanHall;
import com.l2jserver.gameserver.model.zone.type.L2ClanHallZone;

/**
 *
 * @author  Steuf
 */
public class ClanHallManager
{
	protected static final Logger _log = Logger.getLogger(ClanHallManager.class.getName());
	
	private Map<Integer, ClanHall> _clanHall;
	private Map<Integer, ClanHall> _freeClanHall;
	private Map<Integer, ClanHall> _allClanHalls;
	private boolean _loaded = false;
	
	public static ClanHallManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	public boolean loaded()
	{
		return _loaded;
	}
	
	private ClanHallManager()
	{
		_log.info("Initializing ClanHallManager");
		_clanHall = new FastMap<Integer, ClanHall>();
		_freeClanHall = new FastMap<Integer, ClanHall>();
		_allClanHalls = new FastMap<Integer, ClanHall>();
		load();
	}
	
	/** Reload All Clan Hall */
	/*	public final void reload() Cant reload atm - would loose zone info
		{
			_clanHall.clear();
			_freeClanHall.clear();
			load();
		}
	 */
	
	/** Load All Clan Hall */
	private final void load()
	{
		Connection con = null;
		try
		{
			int id, ownerId, lease, grade = 0;
			String Name, Desc, Location;
			long paidUntil = 0;
			boolean paid = false;
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("SELECT * FROM clanhall ORDER BY id");
			ResultSet rs = statement.executeQuery();
			while (rs.next())
			{
				id = rs.getInt("id");
				Name = rs.getString("name");
				ownerId = rs.getInt("ownerId");
				lease = rs.getInt("lease");
				Desc = rs.getString("desc");
				Location = rs.getString("location");
				paidUntil = rs.getLong("paidUntil");
				grade = rs.getInt("Grade");
				paid = rs.getBoolean("paid");
				
				ClanHall ch = new ClanHall(id, Name, ownerId, lease, Desc, Location, paidUntil, grade, paid);
				_allClanHalls.put(id, ch);
				
				if (ownerId > 0)
				{
					final L2Clan owner = ClanTable.getInstance().getClan(ownerId);
					if (owner != null)
					{
						_clanHall.put(id, ch);
						owner.setHasHideout(id);
						continue;
					}
					else
						ch.free();
				}
				_freeClanHall.put(id, ch);
				
				Auction auc = AuctionManager.getInstance().getAuction(id);
				if (auc == null && lease > 0)
					AuctionManager.getInstance().initNPC(id);
			}
			
			rs.close();
			statement.close();
			_log.info("Loaded: " + getClanHalls().size() + " clan halls");
			_log.info("Loaded: " + getFreeClanHalls().size() + " free clan halls");
			_loaded = true;
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Exception: ClanHallManager.load(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/** Get Map with all FreeClanHalls */
	public final Map<Integer, ClanHall> getFreeClanHalls()
	{
		return _freeClanHall;
	}
	
	/** Get Map with all ClanHalls that have owner*/
	public final Map<Integer, ClanHall> getClanHalls()
	{
		return _clanHall;
	}
	
	/** Get Map with all ClanHalls*/
	public final Map<Integer, ClanHall> getAllClanHalls()
	{
		return _allClanHalls;
	}
	
	/** Check is free ClanHall */
	public final boolean isFree(int chId)
	{
		if (_freeClanHall.containsKey(chId))
			return true;
		return false;
	}
	
	/** Free a ClanHall */
	public final synchronized void setFree(int chId)
	{
		_freeClanHall.put(chId, _clanHall.get(chId));
		ClanTable.getInstance().getClan(_freeClanHall.get(chId).getOwnerId()).setHasHideout(0);
		_freeClanHall.get(chId).free();
		_clanHall.remove(chId);
	}
	
	/** Set ClanHallOwner */
	public final synchronized void setOwner(int chId, L2Clan clan)
	{
		if (!_clanHall.containsKey(chId))
		{
			_clanHall.put(chId, _freeClanHall.get(chId));
			_freeClanHall.remove(chId);
		}
		else
			_clanHall.get(chId).free();
		ClanTable.getInstance().getClan(clan.getClanId()).setHasHideout(chId);
		_clanHall.get(chId).setOwner(clan);
	}
	
	/** Get Clan Hall by Id */
	public final ClanHall getClanHallById(int clanHallId)
	{
		if (_clanHall.containsKey(clanHallId))
			return _clanHall.get(clanHallId);
		if (_freeClanHall.containsKey(clanHallId))
			return _freeClanHall.get(clanHallId);
		_log.warning("Clan hall id " + clanHallId + " not found in clanhall table!");
		return null;
	}
	
	/** Get Clan Hall by x,y,z */
	public final ClanHall getClanHall(int x, int y, int z)
	{
		for (ClanHall temp : getClanHalls().values())
		{
			if (temp.checkIfInZone(x, y, z))
				return temp;
		}
		return null;
	}
	
	public final ClanHall getClanHall(L2Object activeObject)
	{
		return getClanHall(activeObject.getX(), activeObject.getY(), activeObject.getZ());
	}
	
	public final ClanHall getNearbyClanHall(int x, int y, int maxDist)
	{
		L2ClanHallZone zone = null;
		
		for (Map.Entry<Integer, ClanHall> ch : _clanHall.entrySet())
		{
			zone = ch.getValue().getZone();
			if (zone != null && zone.getDistanceToZone(x, y) < maxDist)
				return ch.getValue();
		}
		for (Map.Entry<Integer, ClanHall> ch : _freeClanHall.entrySet())
		{
			zone = ch.getValue().getZone();
			if (zone != null && zone.getDistanceToZone(x, y) < maxDist)
				return ch.getValue();
		}
		return null;
	}
	
	/** Get Clan Hall by Owner */
	public final ClanHall getClanHallByOwner(L2Clan clan)
	{
		for (Map.Entry<Integer, ClanHall> ch : _clanHall.entrySet())
		{
			if (clan.getClanId() == ch.getValue().getOwnerId())
				return ch.getValue();
		}
		return null;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final ClanHallManager _instance = new ClanHallManager();
	}
}