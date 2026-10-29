
package com.extremo.vidas;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        dataFile = new File(getDataFolder(), "vidas.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);

        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("ExtremoVidas activado.");
    }

    private int lives(UUID id) {
        return data.getInt(id.toString(), MAX_LIVES);
    }

    private void setLives(UUID id, int amount) {
        data.set(id.toString(), Math.max(0, Math.min(MAX_LIVES, amount)));
        saveData();
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("No se pudieron guardar las vidas: " + e.getMessage());
        }
    }

    private void updateGameMode(Player player, int previousLives, int newLives) {
        if (newLives <= 0) {
            Bukkit.getScheduler().runTask(this, () -> {
                if (player.isOnline()) player.setGameMode(GameMode.SPECTATOR);
            });
        } else if (previousLives <= 0) {
            Bukkit.getScheduler().runTask(this, () -> {
                if (player.isOnline()) player.setGameMode(GameMode.SURVIVAL);
            });
            player.sendMessage("§a¡Has recuperado una vida y vuelves a supervivencia!");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        if (!data.contains(id.toString())) {
            setLives(id, MAX_LIVES);
        }

        if (lives(id) <= 0) {
            Bukkit.getScheduler().runTask(this, () -> {
                if (player.isOnline()) player.setGameMode(GameMode.SPECTATOR);
            });
            player.sendMessage("§cTe has quedado sin vidas. Estás eliminado y en modo espectador.");
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        int remaining = Math.max(0, lives(player.getUniqueId()) - 1);

        setLives(player.getUniqueId(), remaining);

        if (remaining > 0) {
            player.sendMessage("§eTe quedan §c" + remaining + " §evida(s).");
        } else {
            player.sendMessage("§c¡Te has quedado sin vidas! Tras reaparecer pasarás a espectador.");
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        if (lives(player.getUniqueId()) <= 0) {
            Bukkit.getScheduler().runTask(this, () -> {
                if (player.isOnline() && lives(player.getUniqueId()) <= 0) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            });
        }
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("vidas")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Este comando es para jugadores.");
                return true;
            }

            sender.sendMessage("§aTe quedan §c" + lives(player.getUniqueId()) + "§a/3 vidas.");
            return true;
        }

        if (cmd.equals("darvida")) {
            if (!(sender instanceof Player donor)) {
                sender.sendMessage("Solo un jugador puede donar vidas con este comando.");
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage("§cUso: /darvida <jugador>");
                return true;
            }

            Player recipient = Bukkit.getPlayerExact(args[0]);

            if (recipient == null) {
                sender.sendMessage("§cEse jugador debe estar conectado.");
                return true;
            }

            if (recipient.getUniqueId().equals(donor.getUniqueId())) {
                sender.sendMessage("§cNo puedes darte una vida a ti mismo.");
                return true;
            }

            int donorLives = lives(donor.getUniqueId());
            int recipientLives = lives(recipient.getUniqueId());

            if (donorLives <= 0) {
                sender.sendMessage("§cNo tienes vidas para donar.");
                return true;
            }

            if (recipientLives >= MAX_LIVES) {
                sender.sendMessage("§cEse jugador ya tiene 3 vidas.");
                return true;
            }

            setLives(donor.getUniqueId(), donorLives - 1);
            setLives(recipient.getUniqueId(), recipientLives + 1);

            donor.sendMessage("§eHas donado una vida a §b" + recipient.getName()
                    + "§e. Te quedan §c" + (donorLives - 1) + "§e.");

            recipient.sendMessage("§a" + donor.getName()
                    + " te ha dado una vida. Ahora tienes §c"
                    + (recipientLives + 1) + "§a/3.");

            updateGameMode(recipient, recipientLives, recipientLives + 1);
            updateGameMode(donor, donorLives, donorLives - 1);

            return true;
        }

        if (cmd.equals("vidasadmin")) {
            if (sender instanceof Player player && !player.isOp()) {
                sender.sendMessage("§cNo tienes permiso para usar este comando.");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage("§eUso:");
                sender.sendMessage("§f/vidasadmin ver <jugador>");
                sender.sendMessage("§f/vidasadmin dar <jugador> <cantidad>");
                sender.sendMessage("§f/vidasadmin poner <jugador> <cantidad>");
                return true;
            }

            String action = args[0].toLowerCase();
            Player target = Bukkit.getPlayerExact(args[1]);

            if (target == null) {
                sender.sendMessage("§cEl jugador debe estar conectado.");
                return true;
            }

            int oldLives = lives(target.getUniqueId());

            if (action.equals("ver")) {
                sender.sendMessage("§a" + target.getName() + " tiene §c"
                        + oldLives + "§a/3 vidas.");
                return true;
            }

            if (!action.equals("dar") && !action.equals("poner")) {
                sender.sendMessage("§cAcción inválida. Usa ver, dar o poner.");
                return true;
            }

            if (args.length != 3) {
                sender.sendMessage("§cUso: /vidasadmin " + action
                        + " <jugador> <cantidad>");
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cLa cantidad debe ser un número entero.");
                return true;
            }

            if (amount < 0 || (action.equals("dar") && amount == 0)) {
                sender.sendMessage("§cLa cantidad debe ser positiva.");
                return true;
            }

            int newLives = action.equals("dar") ? oldLives + amount : amount;
            newLives = Math.min(MAX_LIVES, newLives);

            setLives(target.getUniqueId(), newLives);
            updateGameMode(target, oldLives, newLives);

            sender.sendMessage("§a" + target.getName() + " ahora tiene §c"
                    + newLives + "§a/3 vidas.");

            target.sendMessage("§eUn administrador ha actualizado tus vidas: §c"
                    + newLives + "§e/3.");

            return true;
        }

        return false;
    }
}
