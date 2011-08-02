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

import java.util.logging.Logger;

import com.l2jserver.Config;
import com.l2jserver.gameserver.model.BlockList;
import com.l2jserver.gameserver.model.L2Party;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.AskJoinParty;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;


/**
 *  sample
 *  29
 *  42 00 00 10
 *  01 00 00 00
 *
 *  format  cdd
 *
 *
 * @version $Revision: 1.7.4.4 $ $Date: 2005/03/27 15:29:30 $
 */
public final class RequestJoinParty extends L2GameClientPacket
{
	private static final String _C__42_REQUESTJOINPARTY = "[C] 42 RequestJoinParty";
	private static Logger _log = Logger.getLogger(RequestJoinParty.class.getName());
	
	private String _name;
	private int _itemDistribution;
	
	@Override
	protected void readImpl()
	{
		_name = readS();
		_itemDistribution = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2PcInstance requestor = getClient().getActiveChar();
		L2PcInstance target = L2World.getInstance().getPlayer(_name);
		
		if (requestor == null)
			return;
		
		if (target == null)
		{
			requestor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.FIRST_SELECT_USER_TO_INVITE_TO_PARTY));
			return;
		}
		
		if (target.getAppearance().getInvisible())
		{
			requestor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.TARGET_IS_INCORRECT));
			return;
		}
		
		if (target.isInParty())
		{
			SystemMessage msg = SystemMessage.getSystemMessage(SystemMessageId.C1_IS_ALREADY_IN_PARTY);
			msg.addString(target.getName());
			requestor.sendPacket(msg);
			return;
		}
		
		if (BlockList.isBlocked(target, requestor))
		{
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_HAS_ADDED_YOU_TO_IGNORE_LIST);
			sm.addCharName(target);
			requestor.sendPacket(sm);
			return;
		}
		
		if (target == requestor)
		{
			requestor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_HAVE_INVITED_THE_WRONG_TARGET));
			return;
		}
		
		if (target.isCursedWeaponEquipped() || requestor.isCursedWeaponEquipped())
		{
			requestor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.INCORRECT_TARGET));
			return;
		}
		
		if (target.isInJail() || requestor.isInJail())
		{
			requestor.sendMessage("Player is in Jail");
			return;
		}
		
		if (target.getClient().isDetached())
		{
			requestor.sendMessage("Player is in offline mode.");
			return;
		}
		
		if (target.isInOlympiadMode() || requestor.isInOlympiadMode())
		{
			if (target.isInOlympiadMode() != requestor.isInOlympiadMode()
					|| target.getOlympiadGameId() != requestor.getOlympiadGameId()
					|| target.getOlympiadSide() != requestor.getOlympiadSide())
				return;
		}
		
		SystemMessage info = SystemMessage.getSystemMessage(SystemMessageId.C1_INVITED_TO_PARTY);
		info.addCharName(target);
		requestor.sendPacket(info);
		
		if (!requestor.isInParty())     //Asker has no party
		{
			createNewParty(target, requestor);
		}
		else                            //Asker is in party
		{
			if(requestor.getParty().isInDimensionalRift())
			{
				requestor.sendMessage("You can't invite a player when in Dimensional Rift.");
			}
			else
			{
				addTargetToParty(target, requestor);
			}
		}
	}
	
	/**
	 * @param client
	 * @param itemDistribution
	 * @param target
	 * @param requestor
	 */
	private void addTargetToParty(L2PcInstance target, L2PcInstance requestor)
	{
		SystemMessage msg;
		L2Party party = requestor.getParty();
		
		// summary of ppl already in party and ppl that get invitation
		if (!party.isLeader(requestor))
		{
			requestor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.ONLY_LEADER_CAN_INVITE));
			return;
		}
		if (party.getMemberCount() >= 9 )
		{
			requestor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.PARTY_FULL));
			return;
		}
		if (party.getPendingInvitation() && !party.isInvitationRequestExpired())
		{
			requestor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.WAITING_FOR_ANOTHER_REPLY));
			return;
		}
		
		if (!target.isProcessingRequest())
		{
			requestor.onTransactionRequest(target);
			// in case a leader change has happened, use party's mode
			target.sendPacket(new AskJoinParty(requestor.getName(), party.getLootDistribution()));
			party.setPendingInvitation(true);
			
			if (Config.DEBUG)
				_log.fine("sent out a party invitation to:"+target.getName());
			
		}
		else
		{
			msg = SystemMessage.getSystemMessage(SystemMessageId.C1_IS_BUSY_TRY_LATER);
			msg.addString(target.getName());
			requestor.sendPacket(msg);
			
			if (Config.DEBUG)
				_log.warning(requestor.getName() + " already received a party invitation");
		}
		msg = null;
	}
	
	
	/**
	 * @param client
	 * @param itemDistribution
	 * @param target
	 * @param requestor
	 */
	private void createNewParty(L2PcInstance target, L2PcInstance requestor)
	{
		if (!target.isProcessingRequest())
		{
			requestor.setParty(new L2Party(requestor, _itemDistribution));
			
			requestor.onTransactionRequest(target);
			target.sendPacket(new AskJoinParty(requestor.getName(), _itemDistribution));
			requestor.getParty().setPendingInvitation(true);
			
			if (Config.DEBUG)
				_log.fine("sent out a party invitation to:"+target.getName());
			
		}
		else
		{
			requestor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.WAITING_FOR_ANOTHER_REPLY));
			
			if (Config.DEBUG)
				_log.warning(requestor.getName() + " already received a party invitation");
		}
	}
	
	@Override
	public String getType()
	{
		return _C__42_REQUESTJOINPARTY;
	}
}
