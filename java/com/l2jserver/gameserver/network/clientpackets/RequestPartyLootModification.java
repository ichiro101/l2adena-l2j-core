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

import com.l2jserver.gameserver.model.L2Party;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;

/**
 * @author JIV
 */
public class RequestPartyLootModification extends L2GameClientPacket
{
	private static final String _C__D0_78_REQUESTPARTYLOOTMODIFICATION = "[C] D0:78 RequestPartyLootModification";
	
	private byte _mode;
	
	@Override
	protected void readImpl()
	{
		_mode = (byte) readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2PcInstance activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		if (_mode < 0 || _mode > L2Party.ITEM_ORDER_SPOIL)
			return;
		L2Party party = activeChar.getParty();
		if (party == null || _mode == party.getLootDistribution() || party.getLeader() != activeChar)
			return;
		party.requestLootChange(_mode);
	}
	
	@Override
	public String getType()
	{
		return _C__D0_78_REQUESTPARTYLOOTMODIFICATION;
	}
}
