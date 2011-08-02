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

import com.l2jserver.gameserver.model.L2Object;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.serverpackets.ActionFailed;

/**
 * TODO: This class is a copy of AttackRequest, we should get proper structure for both.
 */
public final class Attack extends L2GameClientPacket
{
	private static final String _C__01_ATTACK = "[C] 01 Attack";
	
	// cddddc
	private int _objectId;
	@SuppressWarnings("unused")
	private int _originX;
	@SuppressWarnings("unused")
	private int _originY;
	@SuppressWarnings("unused")
	private int _originZ;
	@SuppressWarnings("unused")
	private int _attackId;
	
	@Override
	protected void readImpl()
	{
		_objectId  = readD();
		_originX  = readD();
		_originY  = readD();
		_originZ  = readD();
		_attackId  = readC(); 	 // 0 for simple click   1 for shift-click
	}
	
	@Override
	protected void runImpl()
	{
		final L2PcInstance activeChar = getClient().getActiveChar();
		if (activeChar == null) return;
		// avoid using expensive operations if not needed
		final L2Object target;
		if (activeChar.getTargetId() == _objectId)
			target = activeChar.getTarget();
		else
			target = L2World.getInstance().findObject(_objectId);
		if (target == null)
			return;
		
		// Players can't attack objects in the other instances
		// except from multiverse
		if (target.getInstanceId() != activeChar.getInstanceId()
				&& activeChar.getInstanceId() != -1)
			return;
		
		// Only GMs can directly attack invisible characters
		if (target instanceof L2PcInstance
				&& ((L2PcInstance)target).getAppearance().getInvisible()
				&& !activeChar.isGM())
			return;
		
		if (activeChar.getTarget() != target)
		{
			target.onAction(activeChar);
		}
		else
		{
			if ((target.getObjectId() != activeChar.getObjectId())
					&& activeChar.getPrivateStoreType() ==0
					&& activeChar.getActiveRequester() ==null)
			{
				//_log.debug("Starting ForcedAttack");
				target.onForcedAttack(activeChar);
				//_log.debug("Ending ForcedAttack");
			}
			else
			{
				sendPacket(ActionFailed.STATIC_PACKET);
			}
		}
	}
	
	@Override
	public String getType()
	{
		return _C__01_ATTACK;
	}
}