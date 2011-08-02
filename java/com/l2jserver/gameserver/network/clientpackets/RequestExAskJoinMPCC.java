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
import com.l2jserver.gameserver.model.L2Skill;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.ExAskJoinMPCC;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;

/**
 * Format: (ch) S
 * @author chris_00
 *
 * D0 0D 00 5A 00 77 00 65 00 72 00 67 00 00 00
 *
 */
public final class RequestExAskJoinMPCC extends L2GameClientPacket
{
	private static final String _C__D0_06_REQUESTEXASKJOINMPCC = "[C] D0:06 RequestExAskJoinMPCC";
	private String _name;
	
	@Override
	protected void readImpl()
	{
		_name = readS();
	}
	
	@Override
	protected void runImpl()
	{
		L2PcInstance activeChar = getClient().getActiveChar();
		if(activeChar == null)
			return;
		
		L2PcInstance player = L2World.getInstance().getPlayer(_name);
		if(player == null)
			return;
		// invite yourself? ;)
		if(activeChar.isInParty() && player.isInParty() && activeChar.getParty().equals(player.getParty()))
			return;
		
		SystemMessage sm;
		//activeChar is in a Party?
		if (activeChar.isInParty())
		{
			L2Party activeParty = activeChar.getParty();
			//activeChar is PartyLeader? && activeChars Party is already in a CommandChannel?
			if (activeParty.getLeader().equals(activeChar))
			{
				// if activeChars Party is in CC, is activeChar CCLeader?
				if (activeParty.isInCommandChannel() && activeParty.getCommandChannel().getChannelLeader().equals(activeChar))
				{
					//in CC and the CCLeader
					//target in a party?
					if (player.isInParty())
					{
						//targets party already in a CChannel?
						if (player.getParty().isInCommandChannel())
						{
							sm = SystemMessage.getSystemMessage(SystemMessageId.C1_ALREADY_MEMBER_OF_COMMAND_CHANNEL);
							sm.addString(player.getName());
							activeChar.sendPacket(sm);
						}
						else
						{
							//ready to open a new CC
							//send request to targets Party's PartyLeader
							askJoinMPCC(activeChar, player);
						}
					}
					else
					{
						activeChar.sendMessage("Your target has no Party.");
					}
					
				}
				else if (activeParty.isInCommandChannel() && !activeParty.getCommandChannel().getChannelLeader().equals(activeChar))
				{
					//in CC, but not the CCLeader
					sm = SystemMessage.getSystemMessage(SystemMessageId.CANNOT_INVITE_TO_COMMAND_CHANNEL);
					activeChar.sendPacket(sm);
				}
				else
				{
					//target in a party?
					if (player.isInParty())
					{
						//targets party already in a CChannel?
						if (player.getParty().isInCommandChannel())
						{
							sm = SystemMessage.getSystemMessage(SystemMessageId.C1_ALREADY_MEMBER_OF_COMMAND_CHANNEL);
							sm.addString(player.getName());
							activeChar.sendPacket(sm);
						}
						else
						{
							//ready to open a new CC
							//send request to targets Party's PartyLeader
							askJoinMPCC(activeChar, player);
						}
					}
					else
					{
						activeChar.sendMessage("Your target has no Party.");
					}
				}
			}
			else
			{
				sm = SystemMessage.getSystemMessage(SystemMessageId.CANNOT_INVITE_TO_COMMAND_CHANNEL);
				activeChar.sendPacket(sm);
			}
		}
		
		
	}
	
	private void askJoinMPCC(L2PcInstance requestor, L2PcInstance target)
	{
		boolean hasRight = false;
		if (requestor.getClan() != null && requestor.getClan().getLeaderId() == requestor.getObjectId()
				&& requestor.getClan().getLevel() >= 5) // Clanleader of lvl5 Clan or higher
			hasRight = true;
		else if (requestor.getInventory().getItemByItemId(8871) != null) // 8871 Strategy Guide. Should destroyed after sucessfull invite?
			hasRight = true;
		else if(requestor.getPledgeClass() >= 5) // At least Baron or higher and the skill Clan Imperium
		{
			for (L2Skill skill : requestor.getAllSkills())
			{
				// Skill Clan Imperium
				if (skill.getId() == 391)
				{
					hasRight = true;
					break;
				}
			}
		}
		
		if (!hasRight)
		{
			requestor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.COMMAND_CHANNEL_ONLY_BY_LEVEL_5_CLAN_LEADER_PARTY_LEADER));
			return;
		}
		if (!target.isProcessingRequest())
		{
			requestor.onTransactionRequest(target);
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.COMMAND_CHANNEL_CONFIRM_FROM_C1);
			sm.addString(requestor.getName());
			target.getParty().getLeader().sendPacket(sm);
			target.getParty().getLeader().sendPacket(new ExAskJoinMPCC(requestor.getName()));
			
			requestor.sendMessage("You invited "+target.getName()+" to your Command Channel.");
		}
		else
		{
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_IS_BUSY_TRY_LATER);
			sm.addString(target.getName());
			requestor.sendPacket(sm);
			
		}
	}
	
	@Override
	public String getType()
	{
		return _C__D0_06_REQUESTEXASKJOINMPCC;
	}
}
