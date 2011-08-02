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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javolution.util.FastMap;

import com.l2jserver.L2DatabaseFactory;
import com.l2jserver.gameserver.model.L2AccessLevel;
import com.l2jserver.gameserver.model.L2AdminCommandAccessRight;

/**
 * @author FBIagent<br>
 */
public class AdminCommandAccessRights
{
	/** The logger<br> */
	private static Logger _log = Logger.getLogger(AdminCommandAccessRights.class.getName());
	
	private Map<String, L2AdminCommandAccessRight> _adminCommandAccessRights;
	
	/**
	 * Returns the one and only instance of this class<br><br>
	 * 
	 * @return AdminCommandAccessRights: the one and only instance of this class<br>
	 */
	public static AdminCommandAccessRights getInstance()
	{
		return SingletonHolder._instance;
	}
	
	/** The access rights<br> */
	private AdminCommandAccessRights()
	{
		loadAdminCommandAccessRights();
	}
	
	/**
	 * Loads admin command access rights from database<br>
	 */
	private void loadAdminCommandAccessRights()
	{
		_adminCommandAccessRights = new FastMap<String, L2AdminCommandAccessRight>();
		
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement stmt = con.prepareStatement("SELECT * FROM admin_command_access_rights");
			ResultSet rset = stmt.executeQuery();
			String adminCommand = null;
			String accessLevels = null;
			boolean confirm = false;
			
			while (rset.next())
			{
				adminCommand = rset.getString("adminCommand");
				accessLevels = rset.getString("accessLevels");
				confirm = rset.getBoolean("confirmDlg");
				_adminCommandAccessRights.put(adminCommand, new L2AdminCommandAccessRight(adminCommand, accessLevels, confirm));
			}
			rset.close();
			stmt.close();
		}
		catch (SQLException e)
		{
			_log.log(Level.WARNING, "AdminCommandAccessRights: Error loading from database:" + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		_log.info("AdminCommandAccessRights: Loaded " + _adminCommandAccessRights.size() + " from database.");
	}
	
	public boolean hasAccess(String adminCommand, L2AccessLevel accessLevel)
	{
		if (accessLevel.getLevel() == AccessLevels._masterAccessLevelNum)
			return true;
		
		L2AdminCommandAccessRight acar = _adminCommandAccessRights.get(adminCommand);
		
		if (acar == null)
		{
			_log.info("AdminCommandAccessRights: No rights defined for admin command " + adminCommand + ".");
			return false;
		}
		
		return acar.hasAccess(accessLevel);
	}
	
	public boolean requireConfirm(String command)
	{
		L2AdminCommandAccessRight acar = _adminCommandAccessRights.get(command);
		if (acar == null)
		{
			_log.info("AdminCommandAccessRights: No rights defined for admin command " + command + ".");
			return false;
		}
		return _adminCommandAccessRights.get(command).getRequireConfirm();
	}
	
	public void reloadAdminCommandAccessRights()
	{
		loadAdminCommandAccessRights();
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final AdminCommandAccessRights _instance = new AdminCommandAccessRights();
	}
}