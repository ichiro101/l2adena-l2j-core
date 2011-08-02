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

/**
 * Format: (chd) ddd
 * d: Time Left
 * d: Blue Points
 * d: Red Points
 * 
 * @author mrTJO
 */
public class ExCubeGameChangePoints extends L2GameServerPacket
{
	private static final String _S__FE_98_02_EXCUBEGAMECHANGEPOINTS = "[S] FE:98:02 ExCubeGameChangePoints";
	int _timeLeft;
	int _bluePoints;
	int _redPoints;
	
	/**
	 * Change Client Point Counter
	 * 
	 * @param timeLeft Time Left before Minigame's End
	 * @param bluePoints Current Blue Team Points
	 * @param redPoints Current Red Team Points
	 */
	public ExCubeGameChangePoints(int timeLeft, int bluePoints, int redPoints)
	{
		_timeLeft = timeLeft;
		_bluePoints = bluePoints;
		_redPoints = redPoints;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.serverpackets.ServerBasePacket#writeImpl()
	 */
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x98);
		writeD(0x02);
		
		writeD(_timeLeft);
		writeD(_bluePoints);
		writeD(_redPoints);
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.BasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__FE_98_02_EXCUBEGAMECHANGEPOINTS;
	}
	
}