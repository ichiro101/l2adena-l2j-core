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
package com.l2jserver.gameserver.datatables;

import gnu.trove.TIntObjectHashMap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.l2jserver.L2DatabaseFactory;
import com.l2jserver.gameserver.model.L2LvlupData;
import com.l2jserver.gameserver.model.base.ClassId;

/**
 * This class ...
 *
 * @author NightMarez
 * @version $Revision: 1.3.2.4.2.3 $ $Date: 2005/03/27 15:29:18 $
 */
public class LevelUpData
{
	private static final String SELECT_ALL = "SELECT classid, defaulthpbase, defaulthpadd, defaulthpmod, defaultcpbase, defaultcpadd, defaultcpmod, defaultmpbase, defaultmpadd, defaultmpmod, class_lvl FROM lvlupgain";
	private static final String CLASS_LVL = "class_lvl";
	private static final String MP_MOD = "defaultmpmod";
	private static final String MP_ADD = "defaultmpadd";
	private static final String MP_BASE = "defaultmpbase";
	private static final String HP_MOD = "defaulthpmod";
	private static final String HP_ADD = "defaulthpadd";
	private static final String HP_BASE = "defaulthpbase";
	private static final String CP_MOD = "defaultcpmod";
	private static final String CP_ADD = "defaultcpadd";
	private static final String CP_BASE = "defaultcpbase";
	private static final String CLASS_ID = "classid";
	
	private static Logger _log = Logger.getLogger(LevelUpData.class.getName());
	
	private TIntObjectHashMap<L2LvlupData> _lvlTable;
	
	public static LevelUpData getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private LevelUpData()
	{
		_lvlTable = new TIntObjectHashMap<L2LvlupData>();
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(SELECT_ALL);
			ResultSet rset = statement.executeQuery();
			L2LvlupData lvlDat;
			
			while (rset.next())
			{
				lvlDat = new L2LvlupData();
				lvlDat.setClassid(rset.getInt(CLASS_ID));
				lvlDat.setClassLvl(rset.getInt(CLASS_LVL));
				lvlDat.setClassHpBase(rset.getFloat(HP_BASE));
				lvlDat.setClassHpAdd(rset.getFloat(HP_ADD));
				lvlDat.setClassHpModifier(rset.getFloat(HP_MOD));
				lvlDat.setClassCpBase(rset.getFloat(CP_BASE));
				lvlDat.setClassCpAdd(rset.getFloat(CP_ADD));
				lvlDat.setClassCpModifier(rset.getFloat(CP_MOD));
				lvlDat.setClassMpBase(rset.getFloat(MP_BASE));
				lvlDat.setClassMpAdd(rset.getFloat(MP_ADD));
				lvlDat.setClassMpModifier(rset.getFloat(MP_MOD));
				
				_lvlTable.put(lvlDat.getClassid(), lvlDat);
			}
			
			rset.close();
			statement.close();
			
			_log.info("LevelUpData: Loaded " + _lvlTable.size() + " Character Level Up Templates.");
		}
		catch (Exception e)
		{
			_log.log(Level.SEVERE, "Error loading Level Up data.", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * @param template id
	 * @return
	 */
	public L2LvlupData getTemplate(int classId)
	{
		return _lvlTable.get(classId);
	}
	
	public L2LvlupData getTemplate(ClassId classId)
	{
		return _lvlTable.get(classId.getId());
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final LevelUpData _instance = new LevelUpData();
	}
}
