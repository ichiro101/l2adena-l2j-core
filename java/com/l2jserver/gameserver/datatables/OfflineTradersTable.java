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
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.l2jserver.Config;
import com.l2jserver.L2DatabaseFactory;
import com.l2jserver.gameserver.LoginServerThread;
import com.l2jserver.gameserver.model.L2ManufactureItem;
import com.l2jserver.gameserver.model.L2ManufactureList;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.TradeList.TradeItem;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.L2GameClient;
import com.l2jserver.gameserver.network.L2GameClient.GameClientState;

public class OfflineTradersTable
{
	private static Logger _log = Logger.getLogger(OfflineTradersTable.class.getName());
	
	//SQL DEFINITIONS
	private static final String SAVE_OFFLINE_STATUS = "INSERT INTO character_offline_trade (`charId`,`time`,`type`,`title`) VALUES (?,?,?,?)";
	private static final String SAVE_ITEMS = "INSERT INTO character_offline_trade_items (`charId`,`item`,`count`,`price`) VALUES (?,?,?,?)";
	private static final String CLEAR_OFFLINE_TABLE = "DELETE FROM character_offline_trade";
	private static final String CLEAR_OFFLINE_TABLE_ITEMS = "DELETE FROM character_offline_trade_items";
	private static final String LOAD_OFFLINE_STATUS = "SELECT * FROM character_offline_trade";
	private static final String LOAD_OFFLINE_ITEMS = "SELECT * FROM character_offline_trade_items WHERE charId = ?";
	
	public static void storeOffliners()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement stm = con.prepareStatement(CLEAR_OFFLINE_TABLE);
			stm.execute();
			stm.close();
			stm = con.prepareStatement(CLEAR_OFFLINE_TABLE_ITEMS);
			stm.execute();
			stm.close();
			
			con.setAutoCommit(false); // avoid halfway done
			stm = con.prepareStatement(SAVE_OFFLINE_STATUS);
			PreparedStatement stm_items = con.prepareStatement(SAVE_ITEMS);
			
			//TextBuilder items = TextBuilder.newInstance();
			for (L2PcInstance pc : L2World.getInstance().getAllPlayers().values())
			{
				try
				{
					if ((pc.getPrivateStoreType() != L2PcInstance.STORE_PRIVATE_NONE) && (pc.getClient() == null || pc.getClient().isDetached()))
					{
						stm.setInt(1, pc.getObjectId()); //Char Id
						stm.setLong(2, pc.getOfflineStartTime());
						stm.setInt(3, pc.getPrivateStoreType()); //store type
						String title = null;
						
						switch (pc.getPrivateStoreType())
						{
							case L2PcInstance.STORE_PRIVATE_BUY:
								if (!Config.OFFLINE_TRADE_ENABLE)
									continue;
								title = pc.getBuyList().getTitle();
								for (TradeItem i : pc.getBuyList().getItems())
								{
									stm_items.setInt(1, pc.getObjectId());
									stm_items.setInt(2, i.getItem().getItemId());
									stm_items.setLong(3, i.getCount());
									stm_items.setLong(4, i.getPrice());
									stm_items.executeUpdate();
									stm_items.clearParameters();
								}
								break;
							case L2PcInstance.STORE_PRIVATE_SELL:
							case L2PcInstance.STORE_PRIVATE_PACKAGE_SELL:
								if (!Config.OFFLINE_TRADE_ENABLE)
									continue;
								title = pc.getSellList().getTitle();
								for (TradeItem i : pc.getSellList().getItems())
								{
									stm_items.setInt(1, pc.getObjectId());
									stm_items.setInt(2, i.getObjectId());
									stm_items.setLong(3, i.getCount());
									stm_items.setLong(4, i.getPrice());
									stm_items.executeUpdate();
									stm_items.clearParameters();
								}
								break;
							case L2PcInstance.STORE_PRIVATE_MANUFACTURE:
								if (!Config.OFFLINE_CRAFT_ENABLE)
									continue;
								title = pc.getCreateList().getStoreName();
								for (L2ManufactureItem i : pc.getCreateList().getList())
								{
									stm_items.setInt(1, pc.getObjectId());
									stm_items.setInt(2, i.getRecipeId());
									stm_items.setLong(3, 0);
									stm_items.setLong(4, i.getCost());
									stm_items.executeUpdate();
									stm_items.clearParameters();
								}
						}
						stm.setString(4, title);
						stm.executeUpdate();
						stm.clearParameters();
						con.commit(); // flush
					}
				}
				catch (Exception e)
				{
					_log.log(Level.WARNING, "OfflineTradersTable[storeTradeItems()]: Error while saving offline trader: " + pc.getObjectId() + " " + e, e);
				}
			}
			stm.close();
			stm_items.close();
			_log.info("Offline traders stored.");
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING,"OfflineTradersTable[storeTradeItems()]: Error while saving offline traders: " + e,e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public static void restoreOfflineTraders()
	{
		_log.info("Loading offline traders...");
		Connection con = null;
		int nTraders = 0;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement stm = con.prepareStatement(LOAD_OFFLINE_STATUS);
			ResultSet rs = stm.executeQuery();
			while (rs.next())
			{
				long time = rs.getLong("time");
				if (Config.OFFLINE_MAX_DAYS > 0)
				{
					Calendar cal = Calendar.getInstance();
					cal.setTimeInMillis(time);
					cal.add(Calendar.DAY_OF_YEAR, Config.OFFLINE_MAX_DAYS);
					if (cal.getTimeInMillis() <= System.currentTimeMillis())
						continue;
				}
				
				int type = rs.getInt("type");
				if (type == L2PcInstance.STORE_PRIVATE_NONE)
					continue;
				
				L2PcInstance player = null;
				
				try
				{
					L2GameClient client = new L2GameClient(null);
					client.setDetached(true);
					player = L2PcInstance.load(rs.getInt("charId"));
					client.setActiveChar(player);
					player.setOnlineStatus(true, false);
					client.setAccountName(player.getAccountNamePlayer());
					client.setState(GameClientState.IN_GAME);
					player.setClient(client);
					player.setOfflineStartTime(time);
					player.spawnMe(player.getX(), player.getY(), player.getZ());
					LoginServerThread.getInstance().addGameServerLogin(player.getAccountName(), client);
					PreparedStatement stm_items = con.prepareStatement(LOAD_OFFLINE_ITEMS);
					stm_items.setInt(1, player.getObjectId());
					ResultSet items = stm_items.executeQuery();
					
					switch (type)
					{
						case L2PcInstance.STORE_PRIVATE_BUY:
							while (items.next())
							{
								if (player.getBuyList().addItemByItemId(items.getInt(2), items.getLong(3), items.getLong(4)) == null)
									throw new NullPointerException();
							}
							player.getBuyList().setTitle(rs.getString("title"));
							break;
						case L2PcInstance.STORE_PRIVATE_SELL:
						case L2PcInstance.STORE_PRIVATE_PACKAGE_SELL:
							while (items.next())
							{
								if (player.getSellList().addItem(items.getInt(2), items.getLong(3), items.getLong(4)) == null)
									throw new NullPointerException();
							}
							player.getSellList().setTitle(rs.getString("title"));
							player.getSellList().setPackaged(type == L2PcInstance.STORE_PRIVATE_PACKAGE_SELL);
							break;
						case L2PcInstance.STORE_PRIVATE_MANUFACTURE:
							L2ManufactureList createList = new L2ManufactureList();
							while (items.next())
							{
								createList.add(new L2ManufactureItem(items.getInt(2), items.getLong(4)));
							}
							player.setCreateList(createList);
							player.getCreateList().setStoreName(rs.getString("title"));
							break;
					}
					items.close();
					stm_items.close();
					
					player.sitDown();
					if (Config.OFFLINE_SET_NAME_COLOR)
						player.getAppearance().setNameColor(Config.OFFLINE_NAME_COLOR);
					player.setPrivateStoreType(type);
					player.setOnlineStatus(true, true);
					player.restoreEffects();
					player.broadcastUserInfo();
					nTraders++;
				}
				catch (Exception e)
				{
					_log.log(Level.WARNING, "OfflineTradersTable[loadOffliners()]: Error loading trader: "+player,e);
					if (player != null)
					{
						player.deleteMe();
					}
				}
			}
			rs.close();
			stm.close();
			_log.info("Loaded: " +nTraders+ " offline trader(s)");
			stm = con.prepareStatement(CLEAR_OFFLINE_TABLE);
			stm.execute();
			stm.close();
			stm = con.prepareStatement(CLEAR_OFFLINE_TABLE_ITEMS);
			stm.execute();
			stm.close();
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "OfflineTradersTable[loadOffliners()]: Error while loading offline traders: ",e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
}
