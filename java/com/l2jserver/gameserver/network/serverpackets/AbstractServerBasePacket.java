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
 * This class makes runImpl() and writeImpl() abstract for custom classes outside of this package
 *
 * @version $Revision: $ $Date: $
 * @author  galun
 */
public abstract class AbstractServerBasePacket extends L2GameServerPacket
{
	
	/**
	 * @see com.l2jserver.util.network.BaseSendablePacket.ServerBasePacket#runImpl()
	 */
	@Override
	abstract public void runImpl();
	
	/**
	 * @see com.l2jserver.util.network.BaseSendablePacket.ServerBasePacket#writeImpl()
	 */
	@Override
	abstract protected void writeImpl();
	
	/**
	 * @see com.l2jserver.gameserver.BasePacket#getType()
	 */
	@Override
	abstract public String getType();
}
