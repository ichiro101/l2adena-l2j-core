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

import com.l2jserver.gameserver.datatables.ClanTable;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;

public final class RequestReplySurrenderPledgeWar extends L2GameClientPacket
{
	private static final String _C__08_REQUESTREPLYSURRENDERPLEDGEWAR = "[C] 08 RequestReplySurrenderPledgeWar";
	//private static Logger _log = Logger.getLogger(RequestReplySurrenderPledgeWar.class.getName());
	
	private int _answer;
	
	@Override
	protected void readImpl()
	{
		@SuppressWarnings("unused") String _reqName = readS();
		_answer  = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2PcInstance activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		L2PcInstance requestor = activeChar.getActiveRequester();
		if (requestor == null)
			return;
		
		if (_answer == 1)
		{
			requestor.deathPenalty(false, false, false);
			ClanTable.getInstance().deleteclanswars(requestor.getClanId(), activeChar.getClanId());
		}
		else
		{
			//TODO: is there something missing?
		}
		
		activeChar.onTransactionRequest(null);
	}
	
	@Override
	public String getType()
	{
		return _C__08_REQUESTREPLYSURRENDERPLEDGEWAR;
	}
}