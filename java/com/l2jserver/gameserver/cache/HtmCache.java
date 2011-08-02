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
package com.l2jserver.gameserver.cache;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javolution.util.FastMap;

import com.l2jserver.Config;
import com.l2jserver.gameserver.util.Util;

/**
 * @author Layane
 *
 */
public class HtmCache
{
	private static Logger _log = Logger.getLogger(HtmCache.class.getName());
	
	private final Map<Integer, String> _cache;
	
	private int _loadedFiles;
	private long _bytesBuffLen;
	
	public static HtmCache getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private HtmCache()
	{
		if (Config.LAZY_CACHE)
			_cache = new FastMap<Integer, String>().shared();
		else
			_cache = new FastMap<Integer, String>();
		reload();
	}
	
	public void reload()
	{
		reload(Config.DATAPACK_ROOT);
	}
	
	public void reload(File f)
	{
		if (!Config.LAZY_CACHE)
		{
			_log.info("Html cache start...");
			parseDir(f);
			_log.info("Cache[HTML]: " + String.format("%.3f", getMemoryUsage()) + " megabytes on " + getLoadedFiles() + " files loaded");
		}
		else
		{
			_cache.clear();
			_loadedFiles = 0;
			_bytesBuffLen = 0;
			_log.info("Cache[HTML]: Running lazy cache");
		}
	}
	
	public void reloadPath(File f)
	{
		parseDir(f);
		_log.info("Cache[HTML]: Reloaded specified path.");
	}
	
	public double getMemoryUsage()
	{
		return ((float) _bytesBuffLen / 1048576);
	}
	
	public int getLoadedFiles()
	{
		return _loadedFiles;
	}
	
	private static class HtmFilter implements FileFilter
	{
		public boolean accept(File file)
		{
			if (!file.isDirectory())
			{
				return (file.getName().endsWith(".htm") || file.getName().endsWith(".html"));
			}
			return true;
		}
	}
	
	private void parseDir(File dir)
	{
		FileFilter filter = new HtmFilter();
		File[] files = dir.listFiles(filter);
		
		for (File file : files)
		{
			if (!file.isDirectory())
				loadFile(file);
			else
				parseDir(file);
		}
	}
	
	public String loadFile(File file)
	{
		final String relpath = Util.getRelativePath(Config.DATAPACK_ROOT, file);
		final int hashcode = relpath.hashCode();
		
		final HtmFilter filter = new HtmFilter();
		
		if (file.exists() && filter.accept(file) && !file.isDirectory())
		{
			String content;
			FileInputStream fis = null;
			
			try
			{
				fis = new FileInputStream(file);
				BufferedInputStream bis = new BufferedInputStream(fis);
				int bytes = bis.available();
				byte[] raw = new byte[bytes];
				
				bis.read(raw);
				content = new String(raw, "UTF-8");
				content = content.replaceAll("\r\n", "\n");
				
				String oldContent = _cache.get(hashcode);
				
				if (oldContent == null)
				{
					_bytesBuffLen += bytes;
					_loadedFiles++;
				}
				else
				{
					_bytesBuffLen = _bytesBuffLen - oldContent.length() + bytes;
				}
				
				_cache.put(hashcode, content);
				
				return content;
			}
			catch (Exception e)
			{
				_log.log(Level.WARNING, "Problem with htm file " + e.getMessage(), e);
			}
			finally
			{
				try
				{
					fis.close();
				}
				catch (Exception e1)
				{
				}
			}
		}
		
		return null;
	}
	
	public String getHtmForce(String prefix, String path)
	{
		String content = getHtm(prefix, path);
		
		if (content == null)
		{
			content = "<html><body>My text is missing:<br>" + path + "</body></html>";
			_log.warning("Cache[HTML]: Missing HTML page: " + path);
		}
		
		return content;
	}
	
	public String getHtm(String prefix, String path)
	{
		String newPath = null;
		String content;
		if (prefix != null && !prefix.isEmpty())
		{
			newPath = prefix + path;
			content = getHtm(newPath);
			if (content != null)
				return content;
		}
		
		content = getHtm(path);
		if (content != null && newPath != null)
			_cache.put(newPath.hashCode(), content);
		
		return content;
	}
	
	private String getHtm(String path)
	{
		if (path == null || path.isEmpty())
			return ""; // avoid possible NPE
		
		final int hashCode = path.hashCode();
		String content = _cache.get(hashCode);
		
		if (Config.LAZY_CACHE && content == null)
			content = loadFile(new File(Config.DATAPACK_ROOT, path));
		
		return content;
	}
	
	public boolean contains(String path)
	{
		return _cache.containsKey(path.hashCode());
	}
	
	/**
	 * Check if an HTM exists and can be loaded
	 * @param
	 * path The path to the HTM
	 * */
	public boolean isLoadable(String path)
	{
		File file = new File(path);
		HtmFilter filter = new HtmFilter();
		
		if (file.exists() && filter.accept(file) && !file.isDirectory())
			return true;
		
		return false;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final HtmCache _instance = new HtmCache();
	}
}
