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
package com.l2jserver.gameserver.model.olympiad;

import java.util.List;

import javolution.util.FastList;

import com.l2jserver.gameserver.datatables.SpawnTable;
import com.l2jserver.gameserver.model.L2Spawn;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.network.clientpackets.Say2;
import com.l2jserver.gameserver.network.serverpackets.NpcSay;

/**
 * 
 * @author DS
 *
 */
public final class OlympiadAnnouncer implements Runnable
{
	private static final int OLY_MANAGER = 31688;

	private List<L2Spawn> _managers = new FastList<L2Spawn>();
	private int _currentStadium = 0;

	public OlympiadAnnouncer()
	{
		for (L2Spawn spawn : SpawnTable.getInstance().getSpawnTable())
		{
			if (spawn != null && spawn.getNpcid() == OLY_MANAGER)
				_managers.add(spawn);
		}
	}

	public void run()
	{
		OlympiadGameTask task;
		for (int i = OlympiadGameManager.getInstance().getNumberOfStadiums(); --i >= 0; _currentStadium++)
		{
			if (_currentStadium >= OlympiadGameManager.getInstance().getNumberOfStadiums())
				_currentStadium = 0;

			task = OlympiadGameManager.getInstance().getOlympiadTask(_currentStadium);
			if (task != null && task.getGame() != null && task.needAnnounce())
			{
				int msg;
				final String arenaId = String.valueOf(task.getGame().getStadiumId() + 1);
				switch (task.getGame().getType())
				{
					case NON_CLASSED:
						// msg = "Olympiad class-free individual match is going to begin in Arena " + arenaId + " in a moment.";
						msg = 1300166;
						break;
					case CLASSED:
						// msg = "Olympiad class-specific individual match is going to begin in Arena " + arenaId + " in a moment.";
						msg = 1300167;
						break;
					case TEAMS:
						// msg = "Olympiad class-free team match is going to begin in Arena " + arenaId + " in a moment.";
						msg = 1300132;
						break;
					default:
						continue;
				}

				L2Npc manager;
				NpcSay packet;
				for (L2Spawn spawn : _managers)
				{
					manager = spawn.getLastSpawn();
					if (manager != null)
					{
						packet = new NpcSay(manager.getObjectId(), Say2.SHOUT, manager.getNpcId(), msg);
						packet.addStringParameter(arenaId);
						manager.broadcastPacket(packet);
					}
				}
				break;
			}
		}
	}
}
