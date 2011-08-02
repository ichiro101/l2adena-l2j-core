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

import com.l2jserver.gameserver.instancemanager.CastleManager;
import com.l2jserver.gameserver.model.L2Clan;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.entity.Castle;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;

/**
 * @author KenM
 */
public final class RequestJoinSiege extends L2GameClientPacket
{
	private static final String _C__AD_RequestJoinSiege = "[C] AD RequestJoinSiege";
	//private static Logger _log = Logger.getLogger(RequestJoinSiege.class.getName());
	
	private int _castleId;
	private int _isAttacker;
	private int _isJoining;
	
	@Override
	protected void readImpl()
	{
		_castleId = readD();
		_isAttacker = readD();
		_isJoining = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2PcInstance activeChar = getClient().getActiveChar();
		if (activeChar == null)	return;
		
		if ((activeChar.getClanPrivileges() & L2Clan.CP_CS_MANAGE_SIEGE) != L2Clan.CP_CS_MANAGE_SIEGE)
		{
			activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT));
			return;
		}
		
		L2Clan clan = activeChar.getClan();
		if (clan == null) return;
		
		Castle castle = CastleManager.getInstance().getCastleById(_castleId);
		if (castle == null)	return;
		
		if (_isJoining == 1)
		{
			if (System.currentTimeMillis() < clan.getDissolvingExpiryTime())
			{
				activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANT_PARTICIPATE_IN_SIEGE_WHILE_DISSOLUTION_IN_PROGRESS));
				return;
			}
			if (_isAttacker == 1)
				castle.getSiege().registerAttacker(activeChar);
			else
				castle.getSiege().registerDefender(activeChar);
		}
		else
			castle.getSiege().removeSiegeClan(activeChar);
		castle.getSiege().listRegisterClan(activeChar);
	}
	
	@Override
	public String getType()
	{
		return _C__AD_RequestJoinSiege;
	}
}