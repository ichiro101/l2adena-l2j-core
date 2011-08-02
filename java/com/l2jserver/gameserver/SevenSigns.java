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
package com.l2jserver.gameserver;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javolution.util.FastMap;

import com.l2jserver.Config;
import com.l2jserver.L2DatabaseFactory;
import com.l2jserver.gameserver.datatables.SkillTable;
import com.l2jserver.gameserver.instancemanager.CastleManager;
import com.l2jserver.gameserver.instancemanager.MapRegionManager;
import com.l2jserver.gameserver.instancemanager.QuestManager;
import com.l2jserver.gameserver.instancemanager.TerritoryWarManager;
import com.l2jserver.gameserver.model.AutoChatHandler;
import com.l2jserver.gameserver.model.AutoSpawnHandler;
import com.l2jserver.gameserver.model.AutoSpawnHandler.AutoSpawnInstance;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.entity.Castle;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.SSQInfo;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;
import com.l2jserver.gameserver.templates.StatsSet;
import com.l2jserver.gameserver.util.Broadcast;

/**
 *  Seven Signs Engine
 *
 * 
 * 
 *
 *  @author Tempy
 */
public class SevenSigns
{
	protected static final Logger _log = Logger.getLogger(SevenSigns.class.getName());
	
	// Basic Seven Signs Constants \\
	public static final String SEVEN_SIGNS_DATA_FILE = "config/signs.properties";
	public static final String SEVEN_SIGNS_HTML_PATH = "data/html/seven_signs/";
	
	public static final int CABAL_NULL = 0;
	public static final int CABAL_DUSK = 1;
	public static final int CABAL_DAWN = 2;
	
	public static final int SEAL_NULL = 0;
	public static final int SEAL_AVARICE = 1;
	public static final int SEAL_GNOSIS = 2;
	public static final int SEAL_STRIFE = 3;
	
	public static final int PERIOD_COMP_RECRUITING = 0;
	public static final int PERIOD_COMPETITION = 1;
	public static final int PERIOD_COMP_RESULTS = 2;
	public static final int PERIOD_SEAL_VALIDATION = 3;
	
	public static final int PERIOD_START_HOUR = 18;
	public static final int PERIOD_START_MINS = 00;
	public static final int PERIOD_START_DAY = Calendar.MONDAY;
	
	// The quest event and seal validation periods last for approximately one week
	// with a 15 minutes "interval" period sandwiched between them.
	public static final int PERIOD_MINOR_LENGTH = 900000;
	public static final int PERIOD_MAJOR_LENGTH = 604800000 - PERIOD_MINOR_LENGTH;
	
	public static final int ANCIENT_ADENA_ID = 5575;
	public static final int RECORD_SEVEN_SIGNS_ID = 5707;
	public static final int CERTIFICATE_OF_APPROVAL_ID = 6388;
	public static final int RECORD_SEVEN_SIGNS_COST = 500;
	public static final int ADENA_JOIN_DAWN_COST = 50000;
	
	// NPC Related Constants \\
	public static final int ORATOR_NPC_ID = 31094;
	public static final int PREACHER_NPC_ID = 31093;
	public static final int MAMMON_MERCHANT_ID = 31113;
	public static final int MAMMON_BLACKSMITH_ID = 31126;
	public static final int MAMMON_MARKETEER_ID = 31092;
	public static final int LILITH_NPC_ID = 25283;
	public static final int ANAKIM_NPC_ID = 25286;
	public static final int CREST_OF_DAWN_ID = 31170;
	public static final int CREST_OF_DUSK_ID = 31171;
	// Seal Stone Related Constants \\
	public static final int SEAL_STONE_BLUE_ID = 6360;
	public static final int SEAL_STONE_GREEN_ID = 6361;
	public static final int SEAL_STONE_RED_ID = 6362;
	
	public static final int SEAL_STONE_BLUE_VALUE = 3;
	public static final int SEAL_STONE_GREEN_VALUE = 5;
	public static final int SEAL_STONE_RED_VALUE = 10;
	
	public static final int BLUE_CONTRIB_POINTS = 3;
	public static final int GREEN_CONTRIB_POINTS = 5;
	public static final int RED_CONTRIB_POINTS = 10;
	
	private final Calendar _nextPeriodChange = Calendar.getInstance();
	
	protected int _activePeriod;
	protected int _currentCycle;
	protected double _dawnStoneScore;
	protected double _duskStoneScore;
	protected int _dawnFestivalScore;
	protected int _duskFestivalScore;
	protected int _compWinner;
	protected int _previousWinner;
	protected Calendar _lastSave = Calendar.getInstance();
	
	private Map<Integer, StatsSet> _signsPlayerData;
	
	private Map<Integer, Integer> _signsSealOwners;
	private Map<Integer, Integer> _signsDuskSealTotals;
	private Map<Integer, Integer> _signsDawnSealTotals;
	
	private static AutoSpawnInstance _merchantSpawn;
	private static AutoSpawnInstance _blacksmithSpawn;
	private static AutoSpawnInstance _lilithSpawn;
	private static AutoSpawnInstance _anakimSpawn;
	private static Map<Integer, AutoSpawnInstance> _crestofdawnspawns;
	private static Map<Integer, AutoSpawnInstance> _crestofduskspawns;
	private static Map<Integer, AutoSpawnInstance> _oratorSpawns;
	private static Map<Integer, AutoSpawnInstance> _preacherSpawns;
	private static Map<Integer, AutoSpawnInstance> _marketeerSpawns;
	
	private static final String LOAD_DATA =
		"SELECT charId, cabal, seal, red_stones, green_stones, blue_stones, " +
		"ancient_adena_amount, contribution_score FROM seven_signs";
	
	private static final String LOAD_STATUS = "SELECT * FROM seven_signs_status WHERE id=0";
	
	private static final String INSERT_PLAYER =
		"INSERT INTO seven_signs (charId, cabal, seal) VALUES (?,?,?)";
	
	private static final String UPDATE_PLAYER =
		"UPDATE seven_signs SET cabal=?, seal=?, red_stones=?, green_stones=?, blue_stones=?, " +
		"ancient_adena_amount=?, contribution_score=? WHERE charId=?";
	
	private static final String UPDATE_STATUS =
		"UPDATE seven_signs_status SET current_cycle=?, active_period=?, previous_winner=?, " +
		"dawn_stone_score=?, dawn_festival_score=?, dusk_stone_score=?, dusk_festival_score=?, " +
		"avarice_owner=?, gnosis_owner=?, strife_owner=?, avarice_dawn_score=?, gnosis_dawn_score=?, " +
		"strife_dawn_score=?, avarice_dusk_score=?, gnosis_dusk_score=?, strife_dusk_score=?, " +
		"festival_cycle=?, accumulated_bonus0=?, accumulated_bonus1=?, accumulated_bonus2=?," +
		"accumulated_bonus3=?, accumulated_bonus4=?, date=? WHERE id=0";
	
	private SevenSigns()
	{
		_signsPlayerData = new FastMap<Integer, StatsSet>();
		_signsSealOwners = new FastMap<Integer, Integer>();
		_signsDuskSealTotals = new FastMap<Integer, Integer>();
		_signsDawnSealTotals = new FastMap<Integer, Integer>();
		
		try
		{
			restoreSevenSignsData();
		}
		catch (Exception e)
		{
			_log.log(Level.SEVERE, "SevenSigns: Failed to load configuration: " + e.getMessage(), e);
		}
		
		_log.info("SevenSigns: Currently in the " + getCurrentPeriodName() + " period!");
		initializeSeals();
		
		if (isSealValidationPeriod())
			if (getCabalHighestScore() == CABAL_NULL)
				_log.info("SevenSigns: The competition ended with a tie last week.");
			else
				_log.info("SevenSigns: The " + getCabalName(getCabalHighestScore()) + " were victorious last week.");
		else if (getCabalHighestScore() == CABAL_NULL)
			_log.info("SevenSigns: The competition, if the current trend continues, will end in a tie this week.");
		else
			_log.info("SevenSigns: The " + getCabalName(getCabalHighestScore()) + " are in the lead this week.");
		
		long milliToChange = 0;
		if (isNextPeriodChangeInPast())
		{
			_log.info("SevenSigns: Next period change was in the past (server was offline), changing periods now!");
		}
		else
		{
			setCalendarForNextPeriodChange();
			milliToChange = getMilliToPeriodChange();
		}
		
		
		// Schedule a time for the next period change.
		SevenSignsPeriodChange sspc = new SevenSignsPeriodChange();
		ThreadPoolManager.getInstance().scheduleGeneral(sspc, milliToChange);
		
		// Thanks to http://rainbow.arch.scriptmania.com/scripts/timezone_countdown.html for help with this.
		double numSecs = (milliToChange / 1000) % 60;
		double countDown = ((milliToChange / 1000) - numSecs) / 60;
		int numMins = (int) Math.floor(countDown % 60);
		countDown = (countDown - numMins) / 60;
		int numHours = (int) Math.floor(countDown % 24);
		int numDays = (int) Math.floor((countDown - numHours) / 24);
		
		_log.info("SevenSigns: Next period begins in " + numDays + " days, " + numHours + " hours and " + numMins + " mins.");
		
	}
	
	private boolean isNextPeriodChangeInPast()
	{
		Calendar lastPeriodChange = Calendar.getInstance();
		switch (getCurrentPeriod())
		{
			case PERIOD_SEAL_VALIDATION:
			case PERIOD_COMPETITION:
				lastPeriodChange.set(Calendar.DAY_OF_WEEK, PERIOD_START_DAY);
				lastPeriodChange.set(Calendar.HOUR_OF_DAY, PERIOD_START_HOUR);
				lastPeriodChange.set(Calendar.MINUTE, PERIOD_START_MINS);
				lastPeriodChange.set(Calendar.SECOND, 0);
				// if we hit next week, just turn back 1 week
				if (Calendar.getInstance().before(lastPeriodChange))
					lastPeriodChange.add(Calendar.HOUR, -24*7);
				break;
			case PERIOD_COMP_RECRUITING:
			case PERIOD_COMP_RESULTS:
				// because of the short duration of this period, just check it from last save
				lastPeriodChange.setTimeInMillis(_lastSave.getTimeInMillis() + PERIOD_MINOR_LENGTH);
				break;
		}
		
		// because of previous "date" column usage, check only if it already contains usable data for us
		if (_lastSave.getTimeInMillis() > 7 && _lastSave.before(lastPeriodChange))
			return true;
		else
			return false;
	}
	
	/**
	 * Registers all random spawns and auto-chats for Seven Signs NPCs,
	 * along with spawns for the Preachers of Doom and Orators of Revelations
	 * at the beginning of the Seal Validation period.
	 *
	 */
	public void spawnSevenSignsNPC()
	{
		_merchantSpawn = AutoSpawnHandler.getInstance().getAutoSpawnInstance(MAMMON_MERCHANT_ID, false);
		_blacksmithSpawn = AutoSpawnHandler.getInstance().getAutoSpawnInstance(MAMMON_BLACKSMITH_ID, false);
		_marketeerSpawns = AutoSpawnHandler.getInstance().getAutoSpawnInstances(MAMMON_MARKETEER_ID);
		_lilithSpawn = AutoSpawnHandler.getInstance().getAutoSpawnInstance(LILITH_NPC_ID, false);
		_anakimSpawn = AutoSpawnHandler.getInstance().getAutoSpawnInstance(ANAKIM_NPC_ID, false);
		_crestofdawnspawns = AutoSpawnHandler.getInstance().getAutoSpawnInstances(CREST_OF_DAWN_ID);
		_crestofduskspawns = AutoSpawnHandler.getInstance().getAutoSpawnInstances(CREST_OF_DUSK_ID);
		_oratorSpawns = AutoSpawnHandler.getInstance().getAutoSpawnInstances(ORATOR_NPC_ID);
		_preacherSpawns = AutoSpawnHandler.getInstance().getAutoSpawnInstances(PREACHER_NPC_ID);
		
		if (isSealValidationPeriod() || isCompResultsPeriod())
		{
			for (AutoSpawnInstance spawnInst : _marketeerSpawns.values())
				AutoSpawnHandler.getInstance().setSpawnActive(spawnInst, true);
			
			if (getSealOwner(SEAL_GNOSIS) == getCabalHighestScore() && getSealOwner(SEAL_GNOSIS) != CABAL_NULL)
			{
				if (!Config.ANNOUNCE_MAMMON_SPAWN)
					_blacksmithSpawn.setBroadcast(false);
				
				if (!AutoSpawnHandler.getInstance().getAutoSpawnInstance(_blacksmithSpawn.getObjectId(), true).isSpawnActive())
					AutoSpawnHandler.getInstance().setSpawnActive(_blacksmithSpawn, true);
				
				for (AutoSpawnInstance spawnInst : _oratorSpawns.values())
					if (!AutoSpawnHandler.getInstance().getAutoSpawnInstance(spawnInst.getObjectId(), true).isSpawnActive())
						AutoSpawnHandler.getInstance().setSpawnActive(spawnInst, true);
				
				for (AutoSpawnInstance spawnInst : _preacherSpawns.values())
					if (!AutoSpawnHandler.getInstance().getAutoSpawnInstance(spawnInst.getObjectId(), true).isSpawnActive())
						AutoSpawnHandler.getInstance().setSpawnActive(spawnInst, true);
				
				if (!AutoChatHandler.getInstance().getAutoChatInstance(PREACHER_NPC_ID, false).isActive()
						&& !AutoChatHandler.getInstance().getAutoChatInstance(ORATOR_NPC_ID, false).isActive())
					AutoChatHandler.getInstance().setAutoChatActive(true);
			}
			else
			{
				AutoSpawnHandler.getInstance().setSpawnActive(_blacksmithSpawn, false);
				
				for (AutoSpawnInstance spawnInst : _oratorSpawns.values())
					AutoSpawnHandler.getInstance().setSpawnActive(spawnInst, false);
				
				for (AutoSpawnInstance spawnInst : _preacherSpawns.values())
					AutoSpawnHandler.getInstance().setSpawnActive(spawnInst, false);
				
				AutoChatHandler.getInstance().setAutoChatActive(false);
			}
			
			if (getSealOwner(SEAL_AVARICE) == getCabalHighestScore() && getSealOwner(SEAL_AVARICE) != CABAL_NULL)
			{
				if (!Config.ANNOUNCE_MAMMON_SPAWN)
					_merchantSpawn.setBroadcast(false);
				
				if (!AutoSpawnHandler.getInstance().getAutoSpawnInstance(_merchantSpawn.getObjectId(), true).isSpawnActive())
					AutoSpawnHandler.getInstance().setSpawnActive(_merchantSpawn, true);
				
				switch (getCabalHighestScore())
				{
					case CABAL_DAWN:
						if (!AutoSpawnHandler.getInstance().getAutoSpawnInstance(_lilithSpawn.getObjectId(), true).isSpawnActive())
							AutoSpawnHandler.getInstance().setSpawnActive(_lilithSpawn, true);
						
						AutoSpawnHandler.getInstance().setSpawnActive(_anakimSpawn, false);
						
						for (AutoSpawnInstance dawnCrest : _crestofdawnspawns.values())
						{
							if(!AutoSpawnHandler.getInstance().getAutoSpawnInstance(dawnCrest.getObjectId(), true).isSpawnActive())
							{
								AutoSpawnHandler.getInstance().setSpawnActive(dawnCrest, true);
							}
						}
						
						for (AutoSpawnInstance duskCrest : _crestofduskspawns.values())
						{
							AutoSpawnHandler.getInstance().setSpawnActive(duskCrest, false);
						}
						break;
						
					case CABAL_DUSK:
						if (!AutoSpawnHandler.getInstance().getAutoSpawnInstance(_anakimSpawn.getObjectId(), true).isSpawnActive())
							AutoSpawnHandler.getInstance().setSpawnActive(_anakimSpawn, true);
						
						AutoSpawnHandler.getInstance().setSpawnActive(_lilithSpawn, false);
						
						for (AutoSpawnInstance duskCrest : _crestofduskspawns.values())
						{
							if(!AutoSpawnHandler.getInstance().getAutoSpawnInstance(duskCrest.getObjectId(), true).isSpawnActive())
							{
								AutoSpawnHandler.getInstance().setSpawnActive(duskCrest, true);
							}
						}
						
						for (AutoSpawnInstance dawnCrest : _crestofdawnspawns.values())
						{
							AutoSpawnHandler.getInstance().setSpawnActive(dawnCrest, false);
						}
						break;
				}
			}
			else
			{
				AutoSpawnHandler.getInstance().setSpawnActive(_merchantSpawn, false);
				AutoSpawnHandler.getInstance().setSpawnActive(_lilithSpawn, false);
				AutoSpawnHandler.getInstance().setSpawnActive(_anakimSpawn, false);
				for (AutoSpawnInstance dawnCrest : _crestofdawnspawns.values())
				{
					AutoSpawnHandler.getInstance().setSpawnActive(dawnCrest, false);
				}
				for (AutoSpawnInstance duskCrest : _crestofduskspawns.values())
				{
					AutoSpawnHandler.getInstance().setSpawnActive(duskCrest, false);
				}
			}
		}
		else
		{
			AutoSpawnHandler.getInstance().setSpawnActive(_merchantSpawn, false);
			AutoSpawnHandler.getInstance().setSpawnActive(_blacksmithSpawn, false);
			AutoSpawnHandler.getInstance().setSpawnActive(_lilithSpawn, false);
			AutoSpawnHandler.getInstance().setSpawnActive(_anakimSpawn, false);
			for (AutoSpawnInstance dawnCrest : _crestofdawnspawns.values())
			{
				AutoSpawnHandler.getInstance().setSpawnActive(dawnCrest, false);
			}
			for (AutoSpawnInstance duskCrest : _crestofduskspawns.values())
			{
				AutoSpawnHandler.getInstance().setSpawnActive(duskCrest, false);
			}
			for (AutoSpawnInstance spawnInst : _oratorSpawns.values())
				AutoSpawnHandler.getInstance().setSpawnActive(spawnInst, false);
			
			for (AutoSpawnInstance spawnInst : _preacherSpawns.values())
				AutoSpawnHandler.getInstance().setSpawnActive(spawnInst, false);
			
			for (AutoSpawnInstance spawnInst : _marketeerSpawns.values())
				AutoSpawnHandler.getInstance().setSpawnActive(spawnInst, false);
			
			AutoChatHandler.getInstance().setAutoChatActive(false);
		}
	}
	
	public static SevenSigns getInstance()
	{
		return SingletonHolder._instance;
	}
	
	public static long calcContributionScore(long blueCount, long greenCount, long redCount)
	{
		long contrib = blueCount * BLUE_CONTRIB_POINTS;
		contrib += greenCount * GREEN_CONTRIB_POINTS;
		contrib += redCount * RED_CONTRIB_POINTS;
		
		return contrib;
	}
	
	public static long calcAncientAdenaReward(long blueCount, long greenCount, long redCount)
	{
		long reward = blueCount * SEAL_STONE_BLUE_VALUE;
		reward += greenCount * SEAL_STONE_GREEN_VALUE;
		reward += redCount * SEAL_STONE_RED_VALUE;
		
		return reward;
	}
	
	public static final String getCabalShortName(int cabal)
	{
		switch (cabal)
		{
			case CABAL_DAWN:
				return "dawn";
			case CABAL_DUSK:
				return "dusk";
		}
		
		return "No Cabal";
	}
	
	public static final String getCabalName(int cabal)
	{
		switch (cabal)
		{
			case CABAL_DAWN:
				return "Lords of Dawn";
			case CABAL_DUSK:
				return "Revolutionaries of Dusk";
		}
		
		return "No Cabal";
	}
	
	public static final String getSealName(int seal, boolean shortName)
	{
		String sealName = (!shortName) ? "Seal of " : "";
		
		switch (seal)
		{
			case SEAL_AVARICE:
				sealName += "Avarice";
				break;
			case SEAL_GNOSIS:
				sealName += "Gnosis";
				break;
			case SEAL_STRIFE:
				sealName += "Strife";
				break;
		}
		
		return sealName;
	}
	
	public final int getCurrentCycle()
	{
		return _currentCycle;
	}
	
	public final int getCurrentPeriod()
	{
		return _activePeriod;
	}
	
	private final int getDaysToPeriodChange()
	{
		int numDays = _nextPeriodChange.get(Calendar.DAY_OF_WEEK) - PERIOD_START_DAY;
		
		if (numDays < 0)
			return 0 - numDays;
		
		return 7 - numDays;
	}
	
	public final long getMilliToPeriodChange()
	{
		long currTimeMillis = System.currentTimeMillis();
		long changeTimeMillis = _nextPeriodChange.getTimeInMillis();
		
		return (changeTimeMillis - currTimeMillis);
	}
	
	protected void setCalendarForNextPeriodChange()
	{
		// Calculate the number of days until the next period
		// A period starts at 18:00 pm (local time), like on official servers.
		switch (getCurrentPeriod())
		{
			case PERIOD_SEAL_VALIDATION:
			case PERIOD_COMPETITION:
				int daysToChange = getDaysToPeriodChange();
				
				if (daysToChange == 7)
					if (_nextPeriodChange.get(Calendar.HOUR_OF_DAY) < PERIOD_START_HOUR)
						daysToChange = 0;
					else if (_nextPeriodChange.get(Calendar.HOUR_OF_DAY) == PERIOD_START_HOUR && _nextPeriodChange.get(Calendar.MINUTE) < PERIOD_START_MINS)
						daysToChange = 0;
				
				// Otherwise...
				if (daysToChange > 0)
					_nextPeriodChange.add(Calendar.DATE, daysToChange);
				
				_nextPeriodChange.set(Calendar.HOUR_OF_DAY, PERIOD_START_HOUR);
				_nextPeriodChange.set(Calendar.MINUTE, PERIOD_START_MINS);
				break;
			case PERIOD_COMP_RECRUITING:
			case PERIOD_COMP_RESULTS:
				_nextPeriodChange.add(Calendar.MILLISECOND, PERIOD_MINOR_LENGTH);
				break;
		}
		_log.info("SevenSigns: Next period change set to " + _nextPeriodChange.getTime());
	}
	
	public final String getCurrentPeriodName()
	{
		String periodName = null;
		
		switch (_activePeriod)
		{
			case PERIOD_COMP_RECRUITING:
				periodName = "Quest Event Initialization";
				break;
			case PERIOD_COMPETITION:
				periodName = "Competition (Quest Event)";
				break;
			case PERIOD_COMP_RESULTS:
				periodName = "Quest Event Results";
				break;
			case PERIOD_SEAL_VALIDATION:
				periodName = "Seal Validation";
				break;
		}
		
		return periodName;
	}
	
	public final boolean isSealValidationPeriod()
	{
		return (_activePeriod == PERIOD_SEAL_VALIDATION);
	}
	
	public final boolean isCompResultsPeriod()
	{
		return (_activePeriod == PERIOD_COMP_RESULTS);
	}
	
	/**
	 * returns true if the given date is in Seal Validation or in Quest Event Results period
	 * @param date
	 */
	public boolean isDateInSealValidPeriod(Calendar date)
	{
		long nextPeriodChange = getMilliToPeriodChange();
		long nextQuestStart = 0;
		long nextValidStart = 0;
		long tillDate = date.getTimeInMillis() - Calendar.getInstance().getTimeInMillis();
		while ((2 * PERIOD_MAJOR_LENGTH + 2 * PERIOD_MINOR_LENGTH) < tillDate)
			tillDate -= (2 * PERIOD_MAJOR_LENGTH + 2 * PERIOD_MINOR_LENGTH);
		while (tillDate < 0)
			tillDate += (2 * PERIOD_MAJOR_LENGTH + 2 * PERIOD_MINOR_LENGTH);
		
		switch (getCurrentPeriod())
		{
			case PERIOD_COMP_RECRUITING:
				nextValidStart = nextPeriodChange + PERIOD_MAJOR_LENGTH;
				nextQuestStart = nextValidStart + PERIOD_MAJOR_LENGTH + PERIOD_MINOR_LENGTH;
				break;
			case PERIOD_COMPETITION:
				nextValidStart = nextPeriodChange;
				nextQuestStart = nextPeriodChange + PERIOD_MAJOR_LENGTH + PERIOD_MINOR_LENGTH;
				break;
			case PERIOD_COMP_RESULTS:
				nextQuestStart = nextPeriodChange + PERIOD_MAJOR_LENGTH;
				nextValidStart = nextQuestStart + PERIOD_MAJOR_LENGTH + PERIOD_MINOR_LENGTH;
				break;
			case PERIOD_SEAL_VALIDATION:
				nextQuestStart = nextPeriodChange;
				nextValidStart = nextPeriodChange + PERIOD_MAJOR_LENGTH + PERIOD_MINOR_LENGTH;
				break;
		}
		
		if ((nextQuestStart < tillDate && tillDate < nextValidStart)
				|| (nextValidStart < nextQuestStart && (tillDate < nextValidStart || nextQuestStart < tillDate)))
			return false;
		return true;
	}
	
	public final int getCurrentScore(int cabal)
	{
		double totalStoneScore = _dawnStoneScore + _duskStoneScore;
		
		switch (cabal)
		{
			case CABAL_NULL:
				return 0;
			case CABAL_DAWN:
				return Math.round((float) (_dawnStoneScore / ((float) totalStoneScore == 0 ? 1 : totalStoneScore)) * 500)
				+ _dawnFestivalScore;
			case CABAL_DUSK:
				return Math.round((float) (_duskStoneScore / ((float) totalStoneScore == 0 ? 1 : totalStoneScore)) * 500)
				+ _duskFestivalScore;
		}
		
		return 0;
	}
	
	public final double getCurrentStoneScore(int cabal)
	{
		switch (cabal)
		{
			case CABAL_NULL:
				return 0;
			case CABAL_DAWN:
				return _dawnStoneScore;
			case CABAL_DUSK:
				return _duskStoneScore;
		}
		
		return 0;
	}
	
	public final int getCurrentFestivalScore(int cabal)
	{
		switch (cabal)
		{
			case CABAL_NULL:
				return 0;
			case CABAL_DAWN:
				return _dawnFestivalScore;
			case CABAL_DUSK:
				return _duskFestivalScore;
		}
		
		return 0;
	}
	
	public final int getCabalHighestScore()
	{
		if (getCurrentScore(CABAL_DUSK) == getCurrentScore(CABAL_DAWN))
			return CABAL_NULL;
		else if (getCurrentScore(CABAL_DUSK) > getCurrentScore(CABAL_DAWN))
			return CABAL_DUSK;
		else
			return CABAL_DAWN;
	}
	
	public final int getSealOwner(int seal)
	{
		return _signsSealOwners.get(seal);
	}
	
	public final int getSealProportion(int seal, int cabal)
	{
		if (cabal == CABAL_NULL)
			return 0;
		else if (cabal == CABAL_DUSK)
			return _signsDuskSealTotals.get(seal);
		else
			return _signsDawnSealTotals.get(seal);
	}
	
	public final int getTotalMembers(int cabal)
	{
		int cabalMembers = 0;
		String cabalName = getCabalShortName(cabal);
		
		for (StatsSet sevenDat : _signsPlayerData.values())
			if (sevenDat.getString("cabal").equals(cabalName))
				cabalMembers++;
		
		return cabalMembers;
	}
	
	public int getPlayerStoneContrib(int objectId)
	{
		final StatsSet currPlayer = _signsPlayerData.get(objectId);
		if (currPlayer == null)
			return 0;
		
		int stoneCount = 0;
		stoneCount += currPlayer.getInteger("red_stones");
		stoneCount += currPlayer.getInteger("green_stones");
		stoneCount += currPlayer.getInteger("blue_stones");
		
		return stoneCount;
	}
	
	public int getPlayerContribScore(int objectId)
	{
		final StatsSet currPlayer = _signsPlayerData.get(objectId);
		if (currPlayer == null)
			return 0;
		
		return currPlayer.getInteger("contribution_score");
	}
	
	public int getPlayerAdenaCollect(int objectId)
	{
		final StatsSet currPlayer = _signsPlayerData.get(objectId);
		if (currPlayer == null)
			return 0;
		
		return currPlayer.getInteger("ancient_adena_amount");
	}
	
	public int getPlayerSeal(int objectId)
	{
		final StatsSet currPlayer = _signsPlayerData.get(objectId);
		if (currPlayer == null)
			return SEAL_NULL;
		
		return currPlayer.getInteger("seal");
	}
	
	public int getPlayerCabal(int objectId)
	{
		final StatsSet currPlayer = _signsPlayerData.get(objectId);
		if (currPlayer == null)
			return CABAL_NULL;
		
		String playerCabal = currPlayer.getString("cabal");
		if (playerCabal.equalsIgnoreCase("dawn"))
			return CABAL_DAWN;
		else if (playerCabal.equalsIgnoreCase("dusk"))
			return CABAL_DUSK;
		else
			return CABAL_NULL;
	}
	
	/**
	 * Restores all Seven Signs data and settings, usually called at server startup.
	 *
	 * @throws Exception
	 */
	protected void restoreSevenSignsData()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(LOAD_DATA);
			ResultSet rset = statement.executeQuery();
			
			StatsSet sevenDat = null;
			int charObjId;
			
			while (rset.next())
			{
				charObjId = rset.getInt("charId");
				
				sevenDat = new StatsSet();
				sevenDat.set("charId", charObjId);
				sevenDat.set("cabal", rset.getString("cabal"));
				sevenDat.set("seal", rset.getInt("seal"));
				sevenDat.set("red_stones", rset.getInt("red_stones"));
				sevenDat.set("green_stones", rset.getInt("green_stones"));
				sevenDat.set("blue_stones", rset.getInt("blue_stones"));
				sevenDat.set("ancient_adena_amount", rset.getDouble("ancient_adena_amount"));
				sevenDat.set("contribution_score", rset.getDouble("contribution_score"));
				
				if (Config.DEBUG)
					_log.info("SevenSigns: Loaded data from DB for char ID " + charObjId + " (" + sevenDat.getString("cabal") + ")");
				
				_signsPlayerData.put(charObjId, sevenDat);
			}
			
			rset.close();
			statement.close();
			
			statement = con.prepareStatement(LOAD_STATUS);
			rset = statement.executeQuery();
			
			while (rset.next())
			{
				_currentCycle = rset.getInt("current_cycle");
				_activePeriod = rset.getInt("active_period");
				_previousWinner = rset.getInt("previous_winner");
				
				_dawnStoneScore = rset.getDouble("dawn_stone_score");
				_dawnFestivalScore = rset.getInt("dawn_festival_score");
				_duskStoneScore = rset.getDouble("dusk_stone_score");
				_duskFestivalScore = rset.getInt("dusk_festival_score");
				
				_signsSealOwners.put(SEAL_AVARICE, rset.getInt("avarice_owner"));
				_signsSealOwners.put(SEAL_GNOSIS, rset.getInt("gnosis_owner"));
				_signsSealOwners.put(SEAL_STRIFE, rset.getInt("strife_owner"));
				
				_signsDawnSealTotals.put(SEAL_AVARICE, rset.getInt("avarice_dawn_score"));
				_signsDawnSealTotals.put(SEAL_GNOSIS, rset.getInt("gnosis_dawn_score"));
				_signsDawnSealTotals.put(SEAL_STRIFE, rset.getInt("strife_dawn_score"));
				_signsDuskSealTotals.put(SEAL_AVARICE, rset.getInt("avarice_dusk_score"));
				_signsDuskSealTotals.put(SEAL_GNOSIS, rset.getInt("gnosis_dusk_score"));
				_signsDuskSealTotals.put(SEAL_STRIFE, rset.getInt("strife_dusk_score"));
				
				_lastSave.setTimeInMillis(rset.getLong("date"));
			}
			rset.close();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.log(Level.SEVERE, "SevenSigns: Unable to load Seven Signs data from database: " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		// Festival data is loaded now after the Seven Signs engine data.
	}
	
	/**
	 * Saves all Seven Signs player data.
	 * Should be called on period change and shutdown only.
	 * <BR>
	 *
	 * @param player
	 * @param updateSettings
	 * @throws Exception
	 */
	public void saveSevenSignsData()
	{
		if (Config.DEBUG)
			_log.info("SevenSigns: Saving data to disk.");
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(UPDATE_PLAYER);
			
			for (StatsSet sevenDat : _signsPlayerData.values())
			{
				statement.setString(1, sevenDat.getString("cabal"));
				statement.setInt(2, sevenDat.getInteger("seal"));
				statement.setInt(3, sevenDat.getInteger("red_stones"));
				statement.setInt(4, sevenDat.getInteger("green_stones"));
				statement.setInt(5, sevenDat.getInteger("blue_stones"));
				statement.setDouble(6, sevenDat.getDouble("ancient_adena_amount"));
				statement.setDouble(7, sevenDat.getDouble("contribution_score"));
				statement.setInt(8, sevenDat.getInteger("charId"));
				statement.execute();
				statement.clearParameters();
				if (Config.DEBUG)
					_log.info("SevenSigns: Updated data in database for char ID " + sevenDat.getInteger("charId") + " (" + sevenDat.getString("cabal") + ")");
			}
			statement.close();
		}
		catch (SQLException e)
		{
			_log.log(Level.SEVERE, "SevenSigns: Unable to save data to database: " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public final void saveSevenSignsData(int objectId)
	{
		StatsSet sevenDat = _signsPlayerData.get(objectId);
		if (sevenDat == null)
			return;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = con.prepareStatement(UPDATE_PLAYER);
			statement.setString(1, sevenDat.getString("cabal"));
			statement.setInt(2, sevenDat.getInteger("seal"));
			statement.setInt(3, sevenDat.getInteger("red_stones"));
			statement.setInt(4, sevenDat.getInteger("green_stones"));
			statement.setInt(5, sevenDat.getInteger("blue_stones"));
			statement.setDouble(6, sevenDat.getDouble("ancient_adena_amount"));
			statement.setDouble(7, sevenDat.getDouble("contribution_score"));
			statement.setInt(8, sevenDat.getInteger("charId"));
			statement.execute();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.log(Level.SEVERE, "SevenSigns: Unable to save data to database: " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public final void saveSevenSignsStatus()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = con.prepareStatement(UPDATE_STATUS);
			statement.setInt(1, _currentCycle);
			statement.setInt(2, _activePeriod);
			statement.setInt(3, _previousWinner);
			statement.setDouble(4, _dawnStoneScore);
			statement.setInt(5, _dawnFestivalScore);
			statement.setDouble(6, _duskStoneScore);
			statement.setInt(7, _duskFestivalScore);
			statement.setInt(8, _signsSealOwners.get(SEAL_AVARICE));
			statement.setInt(9, _signsSealOwners.get(SEAL_GNOSIS));
			statement.setInt(10, _signsSealOwners.get(SEAL_STRIFE));
			statement.setInt(11, _signsDawnSealTotals.get(SEAL_AVARICE));
			statement.setInt(12, _signsDawnSealTotals.get(SEAL_GNOSIS));
			statement.setInt(13, _signsDawnSealTotals.get(SEAL_STRIFE));
			statement.setInt(14, _signsDuskSealTotals.get(SEAL_AVARICE));
			statement.setInt(15, _signsDuskSealTotals.get(SEAL_GNOSIS));
			statement.setInt(16, _signsDuskSealTotals.get(SEAL_STRIFE));
			statement.setInt(17, SevenSignsFestival.getInstance().getCurrentFestivalCycle());
			
			for (int i = 0; i < SevenSignsFestival.FESTIVAL_COUNT; i++)
				statement.setInt(18 + i, SevenSignsFestival.getInstance().getAccumulatedBonus(i));
			
			_lastSave = Calendar.getInstance();
			statement.setLong(18 + SevenSignsFestival.FESTIVAL_COUNT, _lastSave.getTimeInMillis());
			statement.execute();
			statement.close();
			if (Config.DEBUG)
				_log.info("SevenSigns: Updated data in database.");
		}
		catch (SQLException e)
		{
			_log.log(Level.SEVERE, "SevenSigns: Unable to save data to database: " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Used to reset the cabal details of all players, and update the database.<BR>
	 * Primarily used when beginning a new cycle, and should otherwise never be called.
	 */
	protected void resetPlayerData()
	{
		if (Config.DEBUG)
			_log.info("SevenSigns: Resetting player data for new event period.");
		
		int charObjId;
		
		// Reset each player's contribution data as well as seal and cabal.
		for (StatsSet sevenDat : _signsPlayerData.values())
		{
			charObjId = sevenDat.getInteger("charId");
			
			// Reset the player's cabal and seal information
			sevenDat.set("cabal", "");
			sevenDat.set("seal", SEAL_NULL);
			sevenDat.set("contribution_score", 0);
			
			_signsPlayerData.put(charObjId, sevenDat);
		}
	}
	
	/**
	 * Used to specify cabal-related details for the specified player. This method
	 * checks to see if the player has registered before and will update the database
	 * if necessary.
	 * <BR>
	 * Returns the cabal ID the player has joined.
	 *
	 * @param player
	 * @param chosenCabal
	 * @param chosenSeal
	 * @return int cabal
	 */
	public int setPlayerInfo(int objectId, int chosenCabal, int chosenSeal)
	{
		StatsSet currPlayerData = _signsPlayerData.get(objectId);
		
		if (currPlayerData != null)
		{
			// If the seal validation period has passed,
			// cabal information was removed and so "re-register" player
			currPlayerData.set("cabal", getCabalShortName(chosenCabal));
			currPlayerData.set("seal", chosenSeal);
			
			_signsPlayerData.put(objectId, currPlayerData);
		}
		else
		{
			currPlayerData = new StatsSet();
			currPlayerData.set("charId", objectId);
			currPlayerData.set("cabal", getCabalShortName(chosenCabal));
			currPlayerData.set("seal", chosenSeal);
			currPlayerData.set("red_stones", 0);
			currPlayerData.set("green_stones", 0);
			currPlayerData.set("blue_stones", 0);
			currPlayerData.set("ancient_adena_amount", 0);
			currPlayerData.set("contribution_score", 0);
			
			_signsPlayerData.put(objectId, currPlayerData);
			
			// Update data in database, as we have a new player signing up.
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement statement = con.prepareStatement(INSERT_PLAYER);
				statement.setInt(1, objectId);
				statement.setString(2, getCabalShortName(chosenCabal));
				statement.setInt(3, chosenSeal);
				statement.execute();
				statement.close();
				
				if (Config.DEBUG)
					_log.info("SevenSigns: Inserted data in DB for char ID " + currPlayerData.getInteger("charId") + " ("
							+ currPlayerData.getString("cabal") + ")");
			}
			catch (SQLException e)
			{
				_log.log(Level.SEVERE, "SevenSigns: Failed to save data: " + e.getMessage(), e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		}
		
		// Increasing Seal total score for the player chosen Seal.
		if ("dawn".equals(currPlayerData.getString("cabal")))
			_signsDawnSealTotals.put(chosenSeal, _signsDawnSealTotals.get(chosenSeal) + 1);
		else
			_signsDuskSealTotals.put(chosenSeal, _signsDuskSealTotals.get(chosenSeal) + 1);
		
		if (!Config.ALT_SEVENSIGNS_LAZY_UPDATE)
			saveSevenSignsStatus();
		
		return chosenCabal;
	}
	
	/**
	 * Returns the amount of ancient adena the specified player can claim, if any.<BR>
	 * If removeReward = True, all the ancient adena owed to them is removed, then
	 * DB is updated.
	 *
	 * @param player
	 * @param removeReward
	 * @return int rewardAmount
	 */
	public int getAncientAdenaReward(int objectId, boolean removeReward)
	{
		StatsSet currPlayer = _signsPlayerData.get(objectId);
		int rewardAmount = currPlayer.getInteger("ancient_adena_amount");
		
		currPlayer.set("red_stones", 0);
		currPlayer.set("green_stones", 0);
		currPlayer.set("blue_stones", 0);
		currPlayer.set("ancient_adena_amount", 0);
		
		if (removeReward)
		{
			_signsPlayerData.put(objectId, currPlayer);
			if (!Config.ALT_SEVENSIGNS_LAZY_UPDATE)
			{
				saveSevenSignsData(objectId);
				saveSevenSignsStatus();
			}
		}
		
		return rewardAmount;
	}
	
	/**
	 * Used to add the specified player's seal stone contribution points
	 * to the current total for their cabal. Returns the point score the
	 * contribution was worth.
	 *
	 * Each stone count <B>must be</B> broken down and specified by the stone's color.
	 *
	 * @param player
	 * @param blueCount
	 * @param greenCount
	 * @param redCount
	 * @return int contribScore
	 */
	public long addPlayerStoneContrib(int objectId, long blueCount, long greenCount, long redCount)
	{
		StatsSet currPlayer = _signsPlayerData.get(objectId);
		
		long contribScore = calcContributionScore(blueCount, greenCount, redCount);
		long totalAncientAdena = currPlayer.getLong("ancient_adena_amount") + calcAncientAdenaReward(blueCount, greenCount, redCount);
		long totalContribScore = currPlayer.getLong("contribution_score") + contribScore;
		
		if (totalContribScore > Config.ALT_MAXIMUM_PLAYER_CONTRIB)
			return -1;
		
		currPlayer.set("red_stones", currPlayer.getInteger("red_stones") + redCount);
		currPlayer.set("green_stones", currPlayer.getInteger("green_stones") + greenCount);
		currPlayer.set("blue_stones", currPlayer.getInteger("blue_stones") + blueCount);
		currPlayer.set("ancient_adena_amount", totalAncientAdena);
		currPlayer.set("contribution_score", totalContribScore);
		_signsPlayerData.put(objectId, currPlayer);
		
		switch (getPlayerCabal(objectId))
		{
			case CABAL_DAWN:
				_dawnStoneScore += contribScore;
				break;
			case CABAL_DUSK:
				_duskStoneScore += contribScore;
				break;
		}
		
		if (!Config.ALT_SEVENSIGNS_LAZY_UPDATE)
		{
			saveSevenSignsData(objectId);
			saveSevenSignsStatus();
		}
		
		return contribScore;
	}
	
	/**
	 * Adds the specified number of festival points to the specified cabal.
	 * Remember, the same number of points are <B>deducted from the rival cabal</B>
	 * to maintain proportionality.
	 *
	 * @param cabal
	 * @param amount
	 */
	public void addFestivalScore(int cabal, int amount)
	{
		if (cabal == CABAL_DUSK)
		{
			_duskFestivalScore += amount;
			
			// To prevent negative scores!
			if (_dawnFestivalScore >= amount)
				_dawnFestivalScore -= amount;
		}
		else
		{
			_dawnFestivalScore += amount;
			
			if (_duskFestivalScore >= amount)
				_duskFestivalScore -= amount;
		}
	}
	
	/**
	 * Send info on the current Seven Signs period to the specified player.
	 *
	 * @param player
	 */
	public void sendCurrentPeriodMsg(L2PcInstance player)
	{
		SystemMessage sm = null;
		
		switch (getCurrentPeriod())
		{
			case PERIOD_COMP_RECRUITING:
				sm = SystemMessage.getSystemMessage(SystemMessageId.PREPARATIONS_PERIOD_BEGUN);
				break;
			case PERIOD_COMPETITION:
				sm = SystemMessage.getSystemMessage(SystemMessageId.COMPETITION_PERIOD_BEGUN);
				break;
			case PERIOD_COMP_RESULTS:
				sm = SystemMessage.getSystemMessage(SystemMessageId.RESULTS_PERIOD_BEGUN);
				break;
			case PERIOD_SEAL_VALIDATION:
				sm = SystemMessage.getSystemMessage(SystemMessageId.VALIDATION_PERIOD_BEGUN);
				break;
		}
		
		player.sendPacket(sm);
	}
	
	/**
	 * Sends the built-in system message specified by sysMsgId to all online players.
	 *
	 * @param sysMsgId
	 */
	public void sendMessageToAll(SystemMessageId sysMsgId)
	{
		SystemMessage sm = SystemMessage.getSystemMessage(sysMsgId);
		Broadcast.toAllOnlinePlayers(sm);
	}
	
	/**
	 * Used to initialize the seals for each cabal. (Used at startup or at beginning of a new cycle).
	 * This method should  be called after <B>resetSeals()</B> and <B>calcNewSealOwners()</B> on a new cycle.
	 */
	protected void initializeSeals()
	{
		for (Integer currSeal : _signsSealOwners.keySet())
		{
			int sealOwner = _signsSealOwners.get(currSeal);
			
			if (sealOwner != CABAL_NULL)
				if (isSealValidationPeriod())
					_log.info("SevenSigns: The " + getCabalName(sealOwner) + " have won the " + getSealName(currSeal, false) + ".");
				else
					_log.info("SevenSigns: The " + getSealName(currSeal, false) + " is currently owned by " + getCabalName(sealOwner) + ".");
			else
				_log.info("SevenSigns: The " + getSealName(currSeal, false) + " remains unclaimed.");
		}
	}
	
	/**
	 * Only really used at the beginning of a new cycle, this method resets all seal-related data.
	 */
	protected void resetSeals()
	{
		_signsDawnSealTotals.put(SEAL_AVARICE, 0);
		_signsDawnSealTotals.put(SEAL_GNOSIS, 0);
		_signsDawnSealTotals.put(SEAL_STRIFE, 0);
		_signsDuskSealTotals.put(SEAL_AVARICE, 0);
		_signsDuskSealTotals.put(SEAL_GNOSIS, 0);
		_signsDuskSealTotals.put(SEAL_STRIFE, 0);
	}
	
	/**
	 * Calculates the ownership of the three Seals of the Seven Signs,
	 * based on various criterion.
	 * <BR><BR>
	 * Should only ever called at the beginning of a new cycle.
	 */
	protected void calcNewSealOwners()
	{
		if (Config.DEBUG)
		{
			_log.info("SevenSigns: (Avarice) Dawn = " + _signsDawnSealTotals.get(SEAL_AVARICE) + ", Dusk = "
					+ _signsDuskSealTotals.get(SEAL_AVARICE));
			_log.info("SevenSigns: (Gnosis) Dawn = " + _signsDawnSealTotals.get(SEAL_GNOSIS) + ", Dusk = "
					+ _signsDuskSealTotals.get(SEAL_GNOSIS));
			_log.info("SevenSigns: (Strife) Dawn = " + _signsDawnSealTotals.get(SEAL_STRIFE) + ", Dusk = "
					+ _signsDuskSealTotals.get(SEAL_STRIFE));
		}
		
		for (Integer currSeal : _signsDawnSealTotals.keySet())
		{
			int prevSealOwner = _signsSealOwners.get(currSeal);
			int newSealOwner = CABAL_NULL;
			int dawnProportion = getSealProportion(currSeal, CABAL_DAWN);
			int totalDawnMembers = getTotalMembers(CABAL_DAWN) == 0 ? 1 : getTotalMembers(CABAL_DAWN);
			int dawnPercent = Math.round(((float) dawnProportion / (float) totalDawnMembers) * 100);
			int duskProportion = getSealProportion(currSeal, CABAL_DUSK);
			int totalDuskMembers = getTotalMembers(CABAL_DUSK) == 0 ? 1 : getTotalMembers(CABAL_DUSK);
			int duskPercent = Math.round(((float) duskProportion / (float) totalDuskMembers) * 100);
			
			/*
			 * - If a Seal was already closed or owned by the opponent and the new winner wants
			 *   to assume ownership of the Seal, 35% or more of the members of the Cabal must
			 *   have chosen the Seal. If they chose less than 35%, they cannot own the Seal.
			 *
			 * - If the Seal was owned by the winner in the previous Seven Signs, they can retain
			 *   that seal if 10% or more members have chosen it. If they want to possess a new Seal,
			 *   at least 35% of the members of the Cabal must have chosen the new Seal.
			 */
			switch (prevSealOwner)
			{
				case CABAL_NULL:
					switch (getCabalHighestScore())
					{
						case CABAL_NULL:
							newSealOwner = CABAL_NULL;
							break;
						case CABAL_DAWN:
							if (dawnPercent >= 35)
								newSealOwner = CABAL_DAWN;
							else
								newSealOwner = CABAL_NULL;
							break;
						case CABAL_DUSK:
							if (duskPercent >= 35)
								newSealOwner = CABAL_DUSK;
							else
								newSealOwner = CABAL_NULL;
							break;
					}
					break;
				case CABAL_DAWN:
					switch (getCabalHighestScore())
					{
						case CABAL_NULL:
							if (dawnPercent >= 10)
								newSealOwner = CABAL_DAWN;
							else
								newSealOwner = CABAL_NULL;
							break;
						case CABAL_DAWN:
							if (dawnPercent >= 10)
								newSealOwner = CABAL_DAWN;
							else
								newSealOwner = CABAL_NULL;
							break;
						case CABAL_DUSK:
							if (duskPercent >= 35)
								newSealOwner = CABAL_DUSK;
							else if (dawnPercent >= 10)
								newSealOwner = CABAL_DAWN;
							else
								newSealOwner = CABAL_NULL;
							break;
					}
					break;
				case CABAL_DUSK:
					switch (getCabalHighestScore())
					{
						case CABAL_NULL:
							if (duskPercent >= 10)
								newSealOwner = CABAL_DUSK;
							else
								newSealOwner = CABAL_NULL;
							break;
						case CABAL_DAWN:
							if (dawnPercent >= 35)
								newSealOwner = CABAL_DAWN;
							else if (duskPercent >= 10)
								newSealOwner = CABAL_DUSK;
							else
								newSealOwner = CABAL_NULL;
							break;
						case CABAL_DUSK:
							if (duskPercent >= 10)
								newSealOwner = CABAL_DUSK;
							else
								newSealOwner = CABAL_NULL;
							break;
					}
					break;
			}
			
			_signsSealOwners.put(currSeal, newSealOwner);
			
			// Alert all online players to new seal status.
			switch (currSeal)
			{
				case SEAL_AVARICE:
					if (newSealOwner == CABAL_DAWN)
						sendMessageToAll(SystemMessageId.DAWN_OBTAINED_AVARICE);
					else if (newSealOwner == CABAL_DUSK)
						sendMessageToAll(SystemMessageId.DUSK_OBTAINED_AVARICE);
					break;
				case SEAL_GNOSIS:
					if (newSealOwner == CABAL_DAWN)
						sendMessageToAll(SystemMessageId.DAWN_OBTAINED_GNOSIS);
					else if (newSealOwner == CABAL_DUSK)
						sendMessageToAll(SystemMessageId.DUSK_OBTAINED_GNOSIS);
					break;
				case SEAL_STRIFE:
					if (newSealOwner == CABAL_DAWN)
						sendMessageToAll(SystemMessageId.DAWN_OBTAINED_STRIFE);
					else if (newSealOwner == CABAL_DUSK)
						sendMessageToAll(SystemMessageId.DUSK_OBTAINED_STRIFE);
					
					CastleManager.getInstance().validateTaxes(newSealOwner);
					break;
			}
		}
	}
	
	/**
	 * This method is called to remove all players from catacombs and
	 * necropolises, who belong to the losing cabal.
	 * <BR><BR>
	 * Should only ever called at the beginning of Seal Validation.
	 */
	protected void teleLosingCabalFromDungeons(String compWinner)
	{
		Collection<L2PcInstance> pls = L2World.getInstance().getAllPlayers().values();
		//synchronized (L2World.getInstance().getAllPlayers())
		{
			StatsSet currPlayer;
			for (L2PcInstance onlinePlayer : pls)
			{
				if (onlinePlayer == null)
					continue;
				
				currPlayer = _signsPlayerData.get(onlinePlayer.getObjectId());
				
				if (isSealValidationPeriod() || isCompResultsPeriod())
				{
					if (!onlinePlayer.isGM() && onlinePlayer.isIn7sDungeon() && (currPlayer == null || !currPlayer.getString("cabal").equals(compWinner)))
					{
						onlinePlayer.teleToLocation(MapRegionManager.TeleportWhereType.Town);
						onlinePlayer.setIsIn7sDungeon(false);
						onlinePlayer.sendMessage("You have been teleported to the nearest town due to the beginning of the Seal Validation period.");
					}
				}
				else
				{
					if (!onlinePlayer.isGM() && onlinePlayer.isIn7sDungeon() && (currPlayer == null || !currPlayer.getString("cabal").isEmpty()))
					{
						onlinePlayer.teleToLocation(MapRegionManager.TeleportWhereType.Town);
						onlinePlayer.setIsIn7sDungeon(false);
						onlinePlayer.sendMessage("You have been teleported to the nearest town because you have not signed for any cabal.");
					}
				}
			}
		}
	}
	
	/**
	 * The primary controller of period change of the Seven Signs system.
	 * This runs all related tasks depending on the period that is about to begin.
	 *
	 * @author Tempy
	 */
	protected class SevenSignsPeriodChange implements Runnable
	{
		public void run()
		{
			/*
			 * Remember the period check here refers to the period just ENDED!
			 */
			final int periodEnded = getCurrentPeriod();
			_activePeriod++;
			
			switch (periodEnded)
			{
				case PERIOD_COMP_RECRUITING: // Initialization
					
					// Start the Festival of Darkness cycle.
					SevenSignsFestival.getInstance().startFestivalManager();
					
					// Send message that Competition has begun.
					sendMessageToAll(SystemMessageId.QUEST_EVENT_PERIOD_BEGUN);
					break;
				case PERIOD_COMPETITION: // Results Calculation
					
					// Send message that Competition has ended.
					sendMessageToAll(SystemMessageId.QUEST_EVENT_PERIOD_ENDED);
					
					int compWinner = getCabalHighestScore();
					
					// Schedule a stop of the festival engine and reward highest ranking members from cycle
					SevenSignsFestival.getInstance().getFestivalManagerSchedule().cancel(false);
					SevenSignsFestival.getInstance().rewardHighestRanked();
					
					calcNewSealOwners();
					
					switch (compWinner)
					{
						case CABAL_DAWN:
							sendMessageToAll(SystemMessageId.DAWN_WON);
							break;
						case CABAL_DUSK:
							sendMessageToAll(SystemMessageId.DUSK_WON);
							break;
					}
					
					_previousWinner = compWinner;
					break;
				case PERIOD_COMP_RESULTS: // Seal Validation
					
					// Perform initial Seal Validation set up.
					initializeSeals();
					//Buff/Debuff members of the event when Seal of Strife captured.
					giveCPMult(getSealOwner(SEAL_STRIFE));
					// Send message that Seal Validation has begun.
					sendMessageToAll(SystemMessageId.SEAL_VALIDATION_PERIOD_BEGUN);
					
					// Change next Territory War date
					Quest twQuest = QuestManager.getInstance().getQuest(TerritoryWarManager.qn);
					if (twQuest != null)
						twQuest.startQuestTimer("setNextTWDate", 30000, null, null);
					
					_log.info("SevenSigns: The " + getCabalName(_previousWinner) + " have won the competition with " + getCurrentScore(_previousWinner) + " points!");
					break;
				case PERIOD_SEAL_VALIDATION: // Reset for New Cycle
					
					// Ensure a cycle restart when this period ends.
					_activePeriod = PERIOD_COMP_RECRUITING;
					
					// Send message that Seal Validation has ended.
					sendMessageToAll(SystemMessageId.SEAL_VALIDATION_PERIOD_ENDED);
					//Clear Seal of Strife influence.
					removeCPMult();
					// Reset all data
					resetPlayerData();
					resetSeals();
					
					_currentCycle++;
					// Reset all Festival-related data and remove any unused blood offerings.
					// NOTE: A full update of Festival data in the database is also performed.
					SevenSignsFestival.getInstance().resetFestivalData(false);
					
					_dawnStoneScore = 0;
					_duskStoneScore = 0;
					
					_dawnFestivalScore = 0;
					_duskFestivalScore = 0;
					break;
			}
			
			// Make sure all Seven Signs data is saved for future use.
			saveSevenSignsData();
			saveSevenSignsStatus();
			
			teleLosingCabalFromDungeons(getCabalShortName(getCabalHighestScore()));
			
			SSQInfo ss = new SSQInfo();
			
			Broadcast.toAllOnlinePlayers(ss);
			spawnSevenSignsNPC();
			
			_log.info("SevenSigns: The " + getCurrentPeriodName() + " period has begun!");
			
			setCalendarForNextPeriodChange();
			
			// make sure that all the scheduled siege dates are in the Seal Validation period
			List<Castle> castles = CastleManager.getInstance().getCastles();
			for (Castle castle : castles)
			{
				castle.getSiege().correctSiegeDateTime();
			}
			
			SevenSignsPeriodChange sspc = new SevenSignsPeriodChange();
			ThreadPoolManager.getInstance().scheduleGeneral(sspc, getMilliToPeriodChange());
		}
	}
	
	public boolean checkIsDawnPostingTicket(int itemId)
	{
		//TODO I think it should be some kind of a list in the datapack for compare;
		if (itemId > 6114 && itemId < 6175)
			return true;
		if (itemId > 6801 && itemId < 6812)
			return true;
		if (itemId > 7997 && itemId < 8008)
			return true;
		if (itemId > 7940 && itemId < 7951)
			return true;
		if (itemId > 6294 && itemId < 6307)
			return true;
		if (itemId > 6831 && itemId < 6834)
			return true;
		if (itemId > 8027 && itemId < 8030)
			return true;
		if (itemId > 7970 && itemId < 7973)
			return true;
		return false;
	}
	
	public boolean checkIsRookiePostingTicket(int itemId)
	{
		//TODO I think it should be some kind of a list in the datapack for compare;
		if (itemId > 6174 && itemId < 6295)
			return true;
		if (itemId > 6811 && itemId < 6832)
			return true;
		if (itemId > 7950 && itemId < 7971)
			return true;
		if (itemId > 8007 && itemId < 8028)
			return true;
		return false;
	}
	
	public void giveCPMult(int StrifeOwner)
	{
		int cabal;
		//Gives "Victor of War" passive skill to all online characters with Cabal, which controls Seal of Strife
		for (L2PcInstance character : L2World.getInstance().getAllPlayers().values())
		{
			if (character == null)
				continue;
			
			cabal = getPlayerCabal(character.getObjectId());
			if (cabal != SevenSigns.CABAL_NULL)
				if (cabal == StrifeOwner)
					character.addSkill(SkillTable.FrequentSkill.THE_VICTOR_OF_WAR.getSkill());
				else
					//Gives "The Vanquished of War" passive skill to all online characters with Cabal, which does not control Seal of Strife
					character.addSkill(SkillTable.FrequentSkill.THE_VANQUISHED_OF_WAR.getSkill());
		}
	}
	
	public void removeCPMult()
	{
		for (L2PcInstance character : L2World.getInstance().getAllPlayers().values())
		{
			if (character == null)
				continue;
			
			//Remove SevenSigns' buffs/debuffs.
			character.removeSkill(SkillTable.FrequentSkill.THE_VICTOR_OF_WAR.getSkill());
			character.removeSkill(SkillTable.FrequentSkill.THE_VANQUISHED_OF_WAR.getSkill());
		}
	}
	
	public boolean checkSummonConditions(L2PcInstance activeChar)
	{
		if (activeChar == null)
			return true;
		//Golems cannot be summoned by Dusk when the Seal of Strife is controlled by the Dawn
		if (isSealValidationPeriod())
			if (getSealOwner(SEAL_STRIFE) == CABAL_DAWN)
				if (getPlayerCabal(activeChar.getObjectId()) == CABAL_DUSK)
				{
					activeChar.sendMessage("You cannot summon Siege Golem or Cannon while Seal of Strife posessed by Lords of Dawn.");
					return true;
				}
		
		return false;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final SevenSigns _instance = new SevenSigns();
	}
}
