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
 * @author Migi
 */
public class ExNoticePostSent extends L2GameServerPacket
{
	private static final String _S__FE_B4_EXNOTICEPOSTSENT = "[S] B4 ExNoticePostSent";
	private static final ExNoticePostSent STATIC_PACKET_TRUE = new ExNoticePostSent(true);
	private static final ExNoticePostSent STATIC_PACKET_FALSE = new ExNoticePostSent(false);
	
	public static final ExNoticePostSent valueOf(boolean result)
	{
		return result ? STATIC_PACKET_TRUE : STATIC_PACKET_FALSE;
	}
	
	boolean _showAnim;
	
	public ExNoticePostSent(boolean showAnimation)
	{
		_showAnim = showAnimation;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.serverpackets.ServerBasePacket#writeImpl()
	 */
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0xb4);
		writeD(_showAnim ? 0x01 : 0x00);
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.BasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__FE_B4_EXNOTICEPOSTSENT;
	}
}
