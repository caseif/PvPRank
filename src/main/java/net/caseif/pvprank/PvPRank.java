package net.caseif.pvprank;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class PvPRank extends JavaPlugin implements Listener {

	public final Logger log = Logger.getLogger("Minecraft");

	public void onEnable(){
		// register events
		getServer().getPluginManager().registerEvents(this, this);
		// create data folder
		this.getDataFolder().mkdir();
		// create data table
		Connection conn = null;
		Statement st = null;
		try {
			Class.forName("org.sqlite.JDBC");
			String dbPath = "jdbc:sqlite:" + this.getDataFolder() + File.separator + "players.db";
			conn = DriverManager.getConnection(dbPath);
			st = conn.createStatement();
			st.executeUpdate("CREATE TABLE IF NOT EXISTS players (" +
					"id INTEGER NOT NULL PRIMARY KEY," +
					"username VARCHAR(20) NOT NULL," +
					"kills INTEGER DEFAULT '0' NOT NULL," +
					"deaths INTEGER DEFAULT '0' NOT NULL," +
					"kdr DECIMAL DEFAULT '0' NOT NULL," +
					"rank INTEGER DEFAULT '0' NOT NULL)");
		}
		catch (Exception e){
			e.printStackTrace();
		}
		finally {
			try {
				st.close();
				conn.close();
			}
			catch (Exception e){
				e.printStackTrace();
			}
		}
		log.info(this + " has been enabled!");
	}

	public void onDisable(){
		log.info(this + "has been disabled!");
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent e){
		String p = e.getPlayer().getName();
		insertPlayer(p);
	}

	@EventHandler
	public void onPlayerKill(PlayerDeathEvent e){
		String victim = e.getEntity().getName();
		Player killerP = e.getEntity().getKiller();
		if (killerP != null){
			String killer = killerP.getName();
			killerP = null;
			Connection conn = null;
			Statement st = null;
			ResultSet rs = null;
			try {
				Class.forName("org.sqlite.JDBC");
				String dbPath = "jdbc:sqlite:" + this.getDataFolder() + File.separator + "players.db";
				conn = DriverManager.getConnection(dbPath);
				st = conn.createStatement();
				//check if both players are in the data table
				insertPlayer(victim);
				insertPlayer(killer);

				// update stats
				// update killer stats
				rs = st.executeQuery("SELECT * FROM players WHERE username = '" + killer + "'");
				int kKills = rs.getInt("kills") + 1;
				int kDeaths = rs.getInt("deaths");
				double kKdr = 0;
				if (kDeaths != 0)
					kKdr = (double)kKills / (double)kDeaths;
				else
					kKdr = kKills;
				st.executeUpdate("UPDATE players SET kills = '" + kKills + "', kdr = '" + kKdr + "' WHERE username = '" + killer + "'");

				// update victim stats
				rs = st.executeQuery("SELECT * FROM players WHERE username = '" + victim + "'");
				int vKills = rs.getInt("kills");
				int vDeaths = rs.getInt("deaths") + 1;
				double vKdr = 0;
				if (vDeaths != 0)
					vKdr = (double)vKills / (double)vDeaths;
				else
					vKdr = vKills;
				st.executeUpdate("UPDATE players SET deaths = '" + vDeaths + "', kdr = '" + vKdr + "' WHERE username = '" + victim + "'");
				
				// update ranks
				// killer
				int kRank = 1;
				rs = st.executeQuery("SELECT * FROM players WHERE kdr > '" + kKdr + "'");
				while (rs.next()){
					kRank += 1;
				}
				st.executeUpdate("UPDATE players SET rank = '" + kRank + "' WHERE username = '" + killer + "'");
				// victim
				int vRank = 1;
				rs = st.executeQuery("SELECT * FROM players WHERE kdr > '" + vKdr + "'");
				while (rs.next()){
					vRank += 1;
				}
				st.executeUpdate("UPDATE players SET rank = '" + vRank + "' WHERE username = '" + victim + "'");
			}
			catch (Exception ex){
				ex.printStackTrace();
			}
			finally {
				try {
					conn.close();
					st.close();
					rs.close();
				}
				catch (Exception exc){
					exc.printStackTrace();
				}
			}
		}
	}

	@SuppressWarnings("deprecation")
	@EventHandler
	public void onPlayerChat(AsyncPlayerChatEvent e){
		String p = e.getPlayer().getName();
		insertPlayer(p);
		Connection conn = null;
		Statement st = null;
		ResultSet rs = null;
		try {
			Class.forName("org.sqlite.JDBC");
			String dbPath = "jdbc:sqlite:" + this.getDataFolder() + File.separator + "players.db";
			conn = DriverManager.getConnection(dbPath);
			st = conn.createStatement();
			rs = st.executeQuery("SELECT * FROM players WHERE username = '" + p + "'");
			final int rank = rs.getInt("rank");
			final Player player = e.getPlayer();
			e.getPlayer().setDisplayName(e.getPlayer().getDisplayName() + ChatColor.BLUE + "[" + rank + "]" + ChatColor.WHITE);
			this.getServer().getScheduler().scheduleAsyncDelayedTask(this, new Runnable(){
				public void run(){
					String name = player.getDisplayName();
					name = name.replace(ChatColor.BLUE + "[" + rank + "]" + ChatColor.WHITE, "");
					player.setDisplayName(name);
				}
			}, 1);
		}
		catch (Exception ex){
			ex.printStackTrace();
		}
		finally {
			try {
				conn.close();
				st.close();
				rs.close();
			}
			catch (Exception exc){
				exc.printStackTrace();
			}
		}
	}

	public boolean insertPlayer(String p){
		Connection conn = null;
		Statement st = null;
		ResultSet rs = null;
		try {
			Class.forName("org.sqlite.JDBC");
			String dbPath = "jdbc:sqlite:" + this.getDataFolder() + File.separator + "players.db";
			conn = DriverManager.getConnection(dbPath);
			st = conn.createStatement();
			// check if player is in database
			rs = st.executeQuery("SELECT COUNT(*) FROM players WHERE username = '" + p + "'");
			int i = 0;
			while (rs.next()){
				i = rs.getInt(1);
			}
			if (i == 0){
				// player is not yet in database
				log.info("[PvPRank] Player " + p + " not found in database. Attempting to add...");
				rs = st.executeQuery("SELECT * FROM players WHERE kdr > '0'");
				int j = 1;
				while (rs.next()){
					j += 1;
				}
				st.executeUpdate("INSERT INTO players (username, kills, deaths, kdr, rank) VALUES ('" + p + "', '0', '0', '0', '" + j + "')");
			}
		}
		catch (Exception ex){
			ex.printStackTrace();
			return false;
		}
		finally {
			try {
				conn.close();
				st.close();
				rs.close();
			}
			catch (Exception exc){
				exc.printStackTrace();
			}
		}
		return true;
	}
}
