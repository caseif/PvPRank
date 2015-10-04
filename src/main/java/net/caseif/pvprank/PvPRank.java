// apologies if this code is hard to read
package net.caseif.pvprank;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class PvPRank extends JavaPlugin implements Listener {
	static final Logger log = Logger.getLogger("Minecraft");
	@Override
	public void onEnable(){
		getServer().getPluginManager().registerEvents(this, this);
		// set up the config
		// * adding this in in a later update

		// create the data folder
		this.getDataFolder().mkdir();

		// create the plugin table if it does not exist
		Connection conn = null;
		Statement st = null;
		try{
			Class.forName("org.sqlite.JDBC");
			String dbPath = "jdbc:sqlite:" + this.getDataFolder() + File.separator + "PvPRank.db";
			conn = DriverManager.getConnection(dbPath);
			st = conn.createStatement();
			st.executeUpdate("CREATE TABLE IF NOT EXISTS players (" +
					"id INTEGER NOT NULL PRIMARY KEY," +
					"username VARCHAR(20) NOT NULL," +
					"kills INTEGER NOT NULL," +
					"deaths INTEGER NOT NULL," +
					"kdr DECIMAL NOT NULL," +
					"rank INTEGER NOT NULL)");
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
		log.info(this + " has been disabled!");
	}
	// trigger update when player is killed
	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(PlayerDeathEvent e){
		if (e.getEntityType() == EntityType.PLAYER){
			Player pvictim = e.getEntity();
			Player pkiller = pvictim.getKiller();
			if (pkiller != null){
				// get the connection
				Connection conn = null;
				ResultSet rs = null;
				Statement st = null;
				try{
					String killer = pkiller.getDisplayName();
					String victim = pvictim.getDisplayName();
					Class.forName("org.sqlite.JDBC");
					String dbPath = "jdbc:sqlite:" + this.getDataFolder() + File.separator + "PvPRank.db";
					conn = DriverManager.getConnection(dbPath);
					st = conn.createStatement();

					// update the killers stats
					// first the kills...
					rs = st.executeQuery("SELECT kills FROM players WHERE username='" + killer + "'");
					int irs = rs.getInt("kills");
					int nrs = irs + 1;
					st.executeUpdate("UPDATE players SET kills='" + nrs + "' WHERE username='" + killer + "'");
					// ...then the kdr
					rs = st.executeQuery("SELECT deaths FROM players WHERE username='" + killer + "'");
					int drs = rs.getInt("deaths");
					// avoid dividing by zero
					if (drs == 0){
						drs = 1;
					}
					int kkdr = nrs / drs;
					st.executeUpdate("UPDATE players SET kdr='" + kkdr + "' WHERE username='" + killer + "'");

					// update the victim's stats
					// first the deaths...
					rs = st.executeQuery("SELECT deaths FROM players WHERE username='" + victim + "'");
					int jrs = rs.getInt("deaths");
					int ors = jrs + 1;
					st.executeUpdate("UPDATE players SET deaths='" + ors + "' WHERE username='" + victim + "'");
					// ...then the kdr
					rs = st.executeQuery("SELECT kills FROM players WHERE username='" + victim + "'");
					int krs = rs.getInt("kills");
					// avoid dividing by zero
					if (ors == 0){
						ors = 1;
					}
					int vkdr = krs / ors;
					st.executeUpdate("UPDATE players SET kdr='" + vkdr + "' WHERE username='" + victim + "'");

					// update everyone's rankings
					ResultSet res = st.executeQuery("SELECT id, kdr FROM players");
					while(res.next()) {
						int id = res.getInt("id");
						BigDecimal kdr = new BigDecimal(res.getString("kdr"));
						rs = st.executeQuery("SELECT COUNT(*) FROM players WHERE kdr>'" + kdr + "'"); 
						int hkdr = 1;
						while (rs.next()){
							hkdr = rs.getInt(1);
						}
						int rank1 = hkdr + 1;
						st.executeUpdate("UPDATE players SET rank='" + rank1 + "' WHERE id='" + id + "'");
					}
				}
				catch (Exception f){
					f.printStackTrace();
				}
				finally {
					try {
						conn.close();
					}
					catch (Exception g){
						g.printStackTrace();
					}
				}
			}
		}
	}
	// modify chat names
	@SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerChat(AsyncPlayerChatEvent event){
		final Player p = event.getPlayer();
		String pl = p.getDisplayName();
		// get player's rank
		Connection conn = null;
		ResultSet rs = null;
		Statement st = null;
		try{
			Class.forName("org.sqlite.JDBC");
			String dbPath = "jdbc:sqlite:" + this.getDataFolder() + File.separator + "PvPRank.db";
			conn = DriverManager.getConnection(dbPath);
			st = conn.createStatement();
			rs = st.executeQuery("SELECT rank FROM players WHERE username='" + pl + "'");
			final int rank = rs.getInt("rank");

			// retrieve rank format from config
			// * adding this in in a later update

			// add rank to username as suffix
			if (!event.isCancelled()){
				p.setDisplayName(p.getDisplayName() + ChatColor.BLUE + "[" + rank + "]" + ChatColor.WHITE);
				this.getServer().getScheduler().scheduleAsyncDelayedTask(this, new Runnable(){
					public void run(){
						String name = p.getDisplayName();
						name = name.replace(ChatColor.BLUE + "[" + rank + "]" + ChatColor.WHITE, "");
						p.setDisplayName(name);
					}
				}, 1);
			}
		}
		catch (Exception e){
			e.printStackTrace();
		}
		finally { 
			try {
				rs.close();
				st.close();
				conn.close();
			}
			catch (Exception e){
				e.printStackTrace();
			}
		}
	}
	// insert row when new player joins
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerJoin(PlayerJoinEvent event){
		Player pl = event.getPlayer();
		String player = pl.getDisplayName();
		try {
			Connection conn = null;
			ResultSet rs = null;
			Statement st = null;
			Class.forName("org.sqlite.JDBC");
			String dbPath = "jdbc:sqlite:" + this.getDataFolder() + File.separator + "PvPRank.db";
			conn = DriverManager.getConnection(dbPath);
			st = conn.createStatement();
			rs = st.executeQuery("SELECT EXISTS(SELECT 1 FROM players WHERE username='" + player + "')");
			int ex = rs.getInt(1);
			if (ex == 0){
				int rank = 1;
				int count = 0;
				ResultSet res = st.executeQuery("SELECT COUNT(*) FROM players");
				while (res.next()){
					count = res.getInt(1);
				}
				if (count != 0){
					ResultSet rsrank = st.executeQuery("SELECT MAX(rank) FROM players");
					rank = rsrank.getInt(1);
				}
				st.executeUpdate("INSERT INTO players (username, kills, deaths, kdr, rank) VALUES ('" + player + "', 0, 0, 0.00, " + rank + ")");
			}
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}

	// define onCommand method for config reload
	// * adding this in a later update
}
