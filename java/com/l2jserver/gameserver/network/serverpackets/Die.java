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

import com.l2jserver.gameserver.datatables.AccessLevels;
import com.l2jserver.gameserver.instancemanager.CastleManager;
import com.l2jserver.gameserver.instancemanager.FortManager;
import com.l2jserver.gameserver.instancemanager.TerritoryWarManager;
import com.l2jserver.gameserver.instancemanager.ZoneManager;
import com.l2jserver.gameserver.model.L2AccessLevel;
import com.l2jserver.gameserver.model.L2Clan;
import com.l2jserver.gameserver.model.L2SiegeClan;
import com.l2jserver.gameserver.model.actor.L2Attackable;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.entity.Castle;
import com.l2jserver.gameserver.model.entity.Fort;
import com.l2jserver.gameserver.model.entity.TvTEvent;
import com.l2jserver.gameserver.model.zone.type.L2RespawnZone;

/**
 * sample
 * 0b
 * 952a1048     objectId
 * 00000000 00000000 00000000 00000000 00000000 00000000

 * format  dddddd   rev 377
 * format  ddddddd   rev 417
 *
 * @version $Revision: 1.3.2.1.2.5 $ $Date: 2005/03/27 18:46:18 $
 */
public class Die extends L2GameServerPacket
{
	private static final String _S__0B_DIE = "[S] 00 Die";
	private int _charObjId;
	private boolean _canTeleport;
	private boolean _sweepable;
	private L2AccessLevel _access = AccessLevels._userAccessLevel;
	private L2Clan _clan;
	L2Character _activeChar;
	
	/**
	 * @param _characters
	 */
	public Die(L2Character cha)
	{
		_activeChar = cha;
		if (cha instanceof L2PcInstance) {
			L2PcInstance player = (L2PcInstance)cha;
			_access = player.getAccessLevel();
			_clan=player.getClan();
			
		}
		_charObjId = cha.getObjectId();
		_canTeleport = !((cha instanceof L2PcInstance && TvTEvent.isStarted() && TvTEvent.isPlayerParticipant(_charObjId)) || cha.isPendingRevive());
		if (cha instanceof L2Attackable)
			_sweepable = ((L2Attackable)cha).isSweepActive();
		
	}
	
	@Override
	protected final void writeImpl()
	{
		
		writeC(0x00);
		
		writeD(_charObjId);
		// NOTE:
		// 6d 00 00 00 00 - to nearest village
		// 6d 01 00 00 00 - to hide away
		// 6d 02 00 00 00 - to castle
		// 6d 03 00 00 00 - to siege HQ
		// sweepable
		// 6d 04 00 00 00 - FIXED
		
		writeD(_canTeleport ? 0x01 : 0);                                                   // 6d 00 00 00 00 - to nearest village
		if (_canTeleport && _clan != null && ZoneManager.getInstance().getZone(_activeChar, L2RespawnZone.class) == null)
		{
			boolean isInCastleDefense = false;
			boolean isInFortDefense = false;
			
			L2SiegeClan siegeClan = null;
			Castle castle = CastleManager.getInstance().getCastle(_activeChar);
			Fort fort = FortManager.getInstance().getFort(_activeChar);
			if (castle != null && castle.getSiege().getIsInProgress())
			{
				//siege in progress
				siegeClan = castle.getSiege().getAttackerClan(_clan);
				if (siegeClan == null && castle.getSiege().checkIsDefender(_clan)){
					isInCastleDefense = true;
				}
			}
			else if (fort != null && fort.getSiege().getIsInProgress())
			{
				//siege in progress
				siegeClan = fort.getSiege().getAttackerClan(_clan);
				if (siegeClan == null && fort.getSiege().checkIsDefender(_clan)){
					isInFortDefense = true;
				}
			}
			
			writeD(_clan.getHasHideout() > 0 ? 0x01 : 0x00);            // 6d 01 00 00 00 - to hide away
			writeD(_clan.getHasCastle() > 0  ||
					isInCastleDefense? 0x01 : 0x00);             		// 6d 02 00 00 00 - to castle
			writeD((TerritoryWarManager.getInstance().getFlagForClan(_clan) != null)
					|| (siegeClan != null && !isInCastleDefense && !isInFortDefense
							&& !siegeClan.getFlag().isEmpty()) ? 0x01 : 0x00);       // 6d 03 00 00 00 - to siege HQ
			writeD(_sweepable ? 0x01 : 0x00);                               // sweepable  (blue glow)
			writeD(_access.allowFixedRes() ? 0x01: 0x00);                  // 6d 04 00 00 00 - to FIXED
			writeD(_clan.getHasFort() > 0  || isInFortDefense? 0x01 : 0x00);    // 6d 05 00 00 00 - to fortress
		}
		else
		{
			writeD(0x00);                                               // 6d 01 00 00 00 - to hide away
			writeD(0x00);                                               // 6d 02 00 00 00 - to castle
			writeD(0x00);                                               // 6d 03 00 00 00 - to siege HQ
			writeD(_sweepable ? 0x01 : 0x00);                               // sweepable  (blue glow)
			writeD(_access.allowFixedRes() ? 0x01: 0x00);                  // 6d 04 00 00 00 - to FIXED
			writeD(0x00);    // 6d 05 00 00 00 - to fortress
		}
		//TODO: protocol 152
		/*
        writeC(0); //show die animation
        writeD(0); //agathion ress button
        writeD(0); //additional free space
		 */
	}
	
	/* (non-Javadoc)
	 * @see com.l2jserver.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__0B_DIE;
	}
}
