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

import java.util.List;
import java.util.Map;

import javolution.util.FastList;
import javolution.util.FastMap;

import com.l2jserver.Config;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.entity.ActionKey;
import com.l2jserver.gameserver.network.L2GameClient.GameClientState;

/**
 * @author mrTJO
 */
public class RequestSaveKeyMapping extends L2GameClientPacket
{
	private static String _C__D0_22_REQUESTSAVEKEYMAPPING = "[C] D0:22 RequestSaveKeyMapping";
	
	int _tabNum;
	
	Map<Integer, List<ActionKey>> _keyMap = new FastMap<Integer, List<ActionKey>>();
	Map<Integer, List<Integer>> _catMap = new FastMap<Integer, List<Integer>>();
	
	@Override
	protected void readImpl()
	{
		int category = 0;
		
		readD(); // Unknown
		readD(); // Unknown
		_tabNum = readD();
		for (int i = 0; i < _tabNum; i++)
		{
			int cmd1Size = readC();
			for (int j = 0; j < cmd1Size; j++)
			{
				int cmdId = readC();
				insertCategory(category, cmdId);
			}
			category++;
			
			int cmd2Size = readC();
			for (int j = 0; j < cmd2Size; j++)
			{
				int cmdId = readC();
				insertCategory(category, cmdId);
			}
			category++;
			
			int cmdSize = readD();
			for (int j = 0; j < cmdSize; j++)
			{
				int cmd = readD();
				int key = readD();
				int tgKey1 = readD();
				int tgKey2 = readD();
				int show = readD();
				insertKey(i, cmd, key, tgKey1, tgKey2, show);
			}
		}
		readD();
		readD();
	}
	
	public void insertCategory(int cat, int cmd)
	{
		if (_catMap.containsKey(cat))
			_catMap.get(cat).add(cmd);
		else
		{
			List<Integer> tmp = new FastList<Integer>();
			tmp.add(cmd);
			_catMap.put(cat, tmp);
		}
	}
	
	public void insertKey(int cat, int cmdId, int key, int tgKey1, int tgKey2, int show)
	{
		ActionKey tmk = new ActionKey(cat, cmdId, key, tgKey1, tgKey2, show);
		if (_keyMap.containsKey(cat))
			_keyMap.get(cat).add(tmk);
		else
		{
			List<ActionKey> tmp = new FastList<ActionKey>();
			tmp.add(tmk);
			_keyMap.put(cat, tmp);
		}
	}
	
	@Override
	protected void runImpl()
	{
		L2PcInstance player = getClient().getActiveChar();
		
		if (player == null)
			return;
		if (getClient().getState() != GameClientState.IN_GAME)
			return;
		if (Config.STORE_UI_SETTINGS)
			player.getUISettings().storeAll(_catMap, _keyMap);
	}
	
	@Override
	public String getType()
	{
		return _C__D0_22_REQUESTSAVEKEYMAPPING;
	}
}
