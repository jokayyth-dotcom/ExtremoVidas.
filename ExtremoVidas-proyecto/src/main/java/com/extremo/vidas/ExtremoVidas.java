package com.extremo.vidas;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class ExtremoVidas extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration data;
    private static final int MAX_LIVES = 3;

    @Override public void onEnable() {
        dataFile = new File(getDataFolder(), "vidas.yml");
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(dataFile);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("ExtremoVidas activado.");
    }

    private int lives(UUID id) { return data.getInt(id.toString(), MAX_LIVES); }
    private void setLives(UUID id, int amount) {
        data.set(id.toString(), Math.max(0, Math.min(MAX_LIVES, amount)));
        saveData();
    }
    private void saveData() {
        try { data.save(dataFile); }
        catch (IOException e) { getLogger().severe("No se pudieron guardar las vidas: " + e.getMessage()); }
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (!data.contains(p.getUniqueId().toString())) setLives(p.getUniqueId(), MAX_LIVES);
        if (lives(p.getUniqueId()) <= 0) {
            Bukkit.getScheduler().runTask(this, () -> p.setGameMode(GameMode.SPECTATOR));
            p.sendMessage("§cTe has quedado sin vidas. Estás eliminado y en modo espectador.");
        }
    }

    @EventHandler public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        int remaining = Math.max(0, lives(p.getUniqueId()) - 1);
        setLives(p.getUniqueId(), remaining);
        if (remaining > 0) p.sendMessage("§eTe quedan §c" + remaining + " §evida(s).");
        else p.sendMessage("§c¡Te has quedado sin vidas! Tras reaparecer pasarás a espectador.");
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (lives(p.getUniqueId()) <= 0) {
            Bukkit.getScheduler().runTask(this, () -> p.setGameMode(GameMode.SPECTATOR));
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("vidas")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Este comando es para jugadores."); return true; }
            sender.sendMessage("§aTe quedan §c" + lives(p.getUniqueId()) + "§a/3 vidas.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("darvida")) {
            if (!(sender instanceof Player donor)) { sender.sendMessage("Solo un jugador puede donar vidas."); return true; }
            if (args.length != 1) { sender.sendMessage("§cUso: /darvida <jugador>"); return true; }
            Player recipient = Bukkit.getPlayerExact(args[0]);
            if (recipient == null) { sender.sendMessage("§cEse jugador debe estar conectado."); return true; }
            if (recipient.getUniqueId().equals(donor.getUniqueId())) { sender.sendMessage("§cNo puedes darte una vida a ti mismo."); return true; }
            int donorLives = lives(donor.getUniqueId());
            int recipientLives = lives(recipient.getUniqueId());
            if (donorLives <= 0) { sender.sendMessage("§cNo tienes vidas para donar."); return true; }
            if (recipientLives >= MAX_LIVES) { sender.sendMessage("§cEse jugador ya tiene 3 vidas."); return true; }
            setLives(donor.getUniqueId(), donorLives - 1);
            setLives(recipient.getUniqueId(), recipientLives + 1);
            donor.sendMessage("§eHas donado una vida a §b" + recipient.getName() + "§e. Te quedan §c" + (donorLives - 1) + "§e.");
            recipient.sendMessage("§a" + donor.getName() + " te ha dado una vida. Ahora tienes §c" + (recipientLives + 1) + "§a/3.");
            if (donorLives - 1 <= 0) {
                donor.sendMessage("§cTe has quedado sin vidas. Pasarás a espectador.");
                Bukkit.getScheduler().runTask(this, () -> donor.setGameMode(GameMode.SPECTATOR));
            }
            return true;
        }
        return false;
    }
}
