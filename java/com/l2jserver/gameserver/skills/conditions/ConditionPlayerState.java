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
package com.l2jserver.gameserver.skills.conditions;

import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.base.PlayerState;
import com.l2jserver.gameserver.skills.Env;

/**
 * The Class ConditionPlayerState.
 * @author mkizub
 */
public class ConditionPlayerState extends Condition
{
	private final PlayerState _check;
	private final boolean _required;
	
	/**
	 * Instantiates a new condition player state.
	 * @param check the player state to be verified.
	 * @param required the required value.
	 */
	public ConditionPlayerState(PlayerState check, boolean required)
	{
		_check = check;
		_required = required;
	}
	
	/**
	 * @see com.l2jserver.gameserver.skills.conditions.Condition#testImpl(com.l2jserver.gameserver.skills.Env)
	 */
	@Override
	public boolean testImpl(Env env)
	{
		final L2Character character = env.player;
		L2PcInstance player = null;
		switch (_check)
		{
			case RESTING:
				player = character.getActingPlayer();
				if (player != null)
				{
					return (player.isSitting() == _required);
				}
				return !_required;
			case MOVING:
				return character.isMoving() == _required;
			case RUNNING:
				return character.isRunning() == _required;
			case STANDING:
				player = character.getActingPlayer();
				if (player != null)
				{
					return (_required != (player.isSitting() || player.isMoving()));
				}
				return (_required != character.isMoving());
			case FLYING:
				return (character.isFlying() == _required);
			case BEHIND:
				return (character.isBehindTarget() == _required);
			case FRONT:
				return (character.isInFrontOfTarget() == _required);
			case CHAOTIC:
				player = character.getActingPlayer();
				if (player != null)
				{
					return ((player.getKarma() > 0) == _required);
				}
				return !_required;
			case OLYMPIAD:
				player = character.getActingPlayer();
				if (player != null)
				{
					return (player.isInOlympiadMode() == _required);
				}
				return !_required;
		}
		return !_required;
	}
}
