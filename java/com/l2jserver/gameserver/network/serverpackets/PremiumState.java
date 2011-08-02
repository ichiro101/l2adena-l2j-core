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
 * format: dc
 * @author  GodKratos
 */
public class PremiumState extends L2GameServerPacket
{
	private static final String _S__FE_AA_EXGETBOOKMARKINFO = "[S] FE:AA PremiumState";
	private final int _objectId;
	private final int _state;
	
	public PremiumState(int objectId, int state)
	{
		_objectId = objectId;
		_state = state;
	}
	
	/**
	 * @see com.l2jserver.util.network.BaseSendablePacket.ServerBasePacket#writeImpl()
	 */
	@Override
	protected void writeImpl()
	{
		writeC(0xFE);
		writeH(0xAA);
		writeD(_objectId);
		writeC(_state);
	}
	
	/**
	 * @see com.l2jserver.gameserver.BasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__FE_AA_EXGETBOOKMARKINFO;
	}
}
