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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import javolution.util.FastList;

import com.l2jserver.L2DatabaseFactory;
import com.l2jserver.gameserver.datatables.CharNameTable;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;

/**
 * Support for "Chat with Friends" dialog.
 *
 * This packet is sent only at login.
 *
 * Format: (c) d[dSdddd]
 * d: Total Friend Count
 *
 * d: Friend ID
 * S: Friend Name
 * d: Online/Offline
 * d: Player Object Id (0 if offline)
 * d: Player Class Id
 * d: Player Level
 *
 * @author mrTJO & UnAfraid
 * 
 */
public class FriendListExtended extends L2GameServerPacket
{
	// private static Logger _log = Logger.getLogger(FriendList.class.getName());
	private static final String _S__FA_FRIENDLISTEXTENDED = "[S] 75 FriendListExtended";
	private final List<FriendInfo> _info;
	
	private static class FriendInfo
	{
		int objId;
		String name;
		boolean online;
		int classid;
		int level;
		
		public FriendInfo(int objId, String name, boolean online, int classid, int level)
		{
			this.objId = objId;
			this.name = name;
			this.online = online;
			this.classid = classid;
			this.level = level;
		}
	}
	
	public FriendListExtended(L2PcInstance player)
	{
		_info = new FastList<FriendInfo>(player.getFriendList().size());
		for (int objId : player.getFriendList())
		{
			String name = CharNameTable.getInstance().getNameById(objId);
			L2PcInstance player1 = L2World.getInstance().getPlayer(objId);
			
			boolean online = false;
			int classid = 0;
			int level = 0;
			
			if (player1 == null)
			{
				Connection con = null;
				try
				{
					con = L2DatabaseFactory.getInstance().getConnection();
					PreparedStatement statement = con.prepareStatement("SELECT char_name, online, classid, level FROM characters WHERE charId = ?");
					statement.setInt(1, objId);
					ResultSet rset = statement.executeQuery();
					if (rset.next())
					{
						_info.add(new FriendInfo(objId, rset.getString(1), rset.getInt(2) == 1, rset.getInt(3), rset.getInt(4)));
					}
					else
						continue;
				}
				catch (Exception e)
				{
					// Who cares?
				}
				finally
				{
					L2DatabaseFactory.close(con);
				}
				
				continue;
			}
			
			if (player1.isOnline())
				online = true;
			
			classid = player1.getClassId().getId();
			level = player1.getLevel();
			
			_info.add(new FriendInfo(objId, name, online, classid, level));
		}
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x58);
		writeD(_info.size());
		for (FriendInfo info : _info)
		{
			writeD(info.objId); // character id
			writeS(info.name);
			writeD(info.online ? 0x01 : 0x00); // online
			writeD(info.online ? info.objId : 0x00); // object id if online
			writeD(info.classid);
			writeD(info.level);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.l2jserver.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__FA_FRIENDLISTEXTENDED;
	}
}
