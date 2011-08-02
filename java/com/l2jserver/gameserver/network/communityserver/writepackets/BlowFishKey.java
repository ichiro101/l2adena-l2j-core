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
package com.l2jserver.gameserver.network.communityserver.writepackets;

import org.netcon.BaseWritePacket;

/**
 * @authors  Forsaiken, Gigiikun
 */
public final class BlowFishKey extends BaseWritePacket
{
	public BlowFishKey(final byte[] tempKey)
	{
		super.writeC(0x00);
		super.writeC(0x00);
		super.writeD(tempKey.length);
		super.writeB(tempKey);
	}
}
