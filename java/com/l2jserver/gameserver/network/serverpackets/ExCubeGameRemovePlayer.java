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

import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;

/**
 * Format: (chd) ddd
 * d: Always -1
 * d: Player Team
 * d: Player Object ID
 * 
 * @author mrTJO
 */
public class ExCubeGameRemovePlayer extends L2GameServerPacket
{
	private static final String _S__FE_97_02_EXCUBEGAMEREMOVEPLAYER = "[S] FE:97:02 ExCubeGameRemovePlayer";
	L2PcInstance _player;
	boolean _isRedTeam;
	
	/**
	 * Remove Player from Minigame Waiting List
	 * 
	 * @param player: Player to Remove
	 * @param isRedTeam: Is Player from Red Team?
	 */
	public ExCubeGameRemovePlayer(L2PcInstance player, boolean isRedTeam)
	{
		_player = player;
		_isRedTeam = isRedTeam;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.serverpackets.ServerBasePacket#writeImpl()
	 */
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x97);
		writeD(0x02);
		
		writeD(0xffffffff);
		
		writeD(_isRedTeam ? 0x01 : 0x00);
		writeD(_player.getObjectId());
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.BasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__FE_97_02_EXCUBEGAMEREMOVEPLAYER;
	}
	
}