package com.extremo.vidas;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class ExtremoVidas extends JavaPlugin implements Listener {

    private File dataFile;
    private YamlConfiguration data;

    private static final int MAX_LIVES = 3;

    // El PvP cambia cada 20 minutos.
    private static final long PVP_INTERVAL_TICKS = 20L * 60 * 20;

    // El PvP comienza desactivado.
    private boolean pvpEnabled = false;

    // Ruleta: un evento cada 30 minutos, con desafios de 15 minutos.
    private static final long RULETA_INTERVAL = 20L * 60 * 30;
    private static final int RULETA_DURATION = 20 * 60 * 15;
    private final Random random = new Random();
    private final List<UUID> mobsRuleta = new ArrayList<>();
    private UUID jugadorRuleta;
    private PotionEffectType efectoRuleta;
    private BukkitTask cierreRuleta;

    @Override
    public void onEnable() {

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        dataFile = new File(getDataFolder(), "vidas.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);

        Bukkit.getPluginManager().registerEvents(this, this);

        // Estado inicial del PvP.
        pvpEnabled = false;

        Bukkit.broadcastMessage(
            "§a§l[PVP] §fPvP DESACTIVADO durante 20 minutos."
        );

        // Alternar el PvP cada 20 minutos.
        Bukkit.getScheduler().runTaskTimer(this, () -> {

            pvpEnabled = !pvpEnabled;

            if (pvpEnabled) {

                // PvP ACTIVADO: mensaje rojo.
                Bukkit.broadcastMessage(
                    "§c§l[PVP] §f¡PvP ACTIVADO durante 20 minutos!"
                );

                // Sonido del cuerno de cabra.
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.playSound(
                        player.getLocation(),
                        "minecraft:item.goat_horn.sound.0",
                        1.0f,
                        1.0f
                    );
                }

            } else {

                // PvP DESACTIVADO: mensaje verde.
                Bukkit.broadcastMessage(
                    "§a§l[PVP] §f¡PvP DESACTIVADO durante 20 minutos!"
                );

                // El mismo cuerno de cabra al desactivar el PvP.
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.playSound(
                        player.getLocation(),
                        "minecraft:item.goat_horn.sound.0",
                        1.0f,
                        1.0f
                    );
                }
            }

        }, PVP_INTERVAL_TICKS, PVP_INTERVAL_TICKS);

        // Primera ruleta a los 30 minutos del arranque; luego cada 30.
        Bukkit.getScheduler().runTaskTimer(this, this::iniciarRuleta,
                RULETA_INTERVAL, RULETA_INTERVAL);

        getLogger().info(
            "ExtremoVidas activado con PvP automatico y sonidos."
        );
    }

    // Ruleta global: un jugador elegible y un reto al azar.
    private void iniciarRuleta() {
        // Evita solapamientos si el servidor se retrasa.
        terminarRuleta();

        List<Player> candidatos = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SURVIVAL && !p.isDead()
                    && lives(p.getUniqueId()) > 0) {
                candidatos.add(p);
            }
        }
        if (candidatos.isEmpty()) {
            Bukkit.broadcastMessage("§7[Ruleta] No hay jugadores elegibles esta vez.");
            return;
        }

        Player elegido = candidatos.get(random.nextInt(candidatos.size()));
        jugadorRuleta = elegido.getUniqueId();
        String reto;

        // Mitad de posibilidades: un efecto; mitad: una horda de mobs.
        if (random.nextBoolean()) {
            int opcion = random.nextInt(7);
            PotionEffectType tipo;
            String nombre;
            int duracion = RULETA_DURATION;
            switch (opcion) {
                case 0 -> { tipo = PotionEffectType.BLINDNESS; nombre = "Ceguera"; }
                case 1 -> { tipo = PotionEffectType.SLOWNESS; nombre = "Lentitud"; }
                case 2 -> { tipo = PotionEffectType.WEAKNESS; nombre = "Debilidad"; }
                case 3 -> { tipo = PotionEffectType.MINING_FATIGUE; nombre = "Fatiga minera"; }
                case 4 -> { tipo = PotionEffectType.HUNGER; nombre = "Hambre"; }
                case 5 -> { tipo = PotionEffectType.POISON; nombre = "Veneno"; }
                default -> {
                    // 15 minutos de levitacion continua seria letal por caidas.
                    // Solo 5 segundos de levitacion y 15 min de caida lenta.
                    tipo = PotionEffectType.LEVITATION;
                    nombre = "Levitacion (5 s) + caida lenta (15 min)";
                    duracion = 20 * 5;
                    elegido.addPotionEffect(new PotionEffect(
                            PotionEffectType.SLOW_FALLING, RULETA_DURATION, 0));
                }
            }
            elegido.addPotionEffect(new PotionEffect(tipo, duracion, 0));
            efectoRuleta = tipo;
            reto = nombre;
        } else {
            EntityType[] tipos = {
                    EntityType.WITHER_SKELETON, EntityType.BLAZE,
                    EntityType.SPIDER, EntityType.SKELETON, EntityType.CAVE_SPIDER
            };
            EntityType tipo = tipos[random.nextInt(tipos.length)];
            int cantidad = 20 + random.nextInt(11);
            int creados = 0;
            Location centro = elegido.getLocation();
            for (int i = 0; i < cantidad; i++) {
                // Intentar encontrar un lugar libre cerca, sin modificar bloques.
                Location spawn = buscarSpawnSeguro(centro);
                if (spawn == null) continue;
                LivingEntity mob = (LivingEntity) centro.getWorld().spawnEntity(spawn, tipo);
                mob.setRemoveWhenFarAway(false);
                mobsRuleta.add(mob.getUniqueId());
                creados++;
            }
            reto = "¡" + creados + " " + tipo.name().replace('_', ' ') + "!";
        }

        Bukkit.broadcastMessage("§5§l[ RULETA ] §f" + elegido.getName()
                + " §dha recibido: §c" + reto);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§5§l¡RULETA!", "§f" + elegido.getName() + " §7- §c" + reto,
                    10, 60, 10);
            p.playSound(p.getLocation(), "minecraft:item.goat_horn.sound.0", 1.0f, 1.0f);
        }
        elegido.sendMessage("§c[Ruleta] El reto termina en 15 minutos.");
        cierreRuleta = Bukkit.getScheduler().runTaskLater(this, this::terminarRuleta,
                RULETA_DURATION);
    }

    // Busca un espacio despejado en superficie cercana; evita generar mobs dentro de bloques.
    private Location buscarSpawnSeguro(Location centro) {
        for (int intento = 0; intento < 18; intento++) {
            int dx = random.nextInt(17) - 8;
            int dz = random.nextInt(17) - 8;
            int x = centro.getBlockX() + dx;
            int z = centro.getBlockZ() + dz;
            for (int dy = -4; dy <= 4; dy++) {
                int y = centro.getBlockY() + dy;
                if (y <= centro.getWorld().getMinHeight() + 1
                        || y >= centro.getWorld().getMaxHeight() - 2) continue;
                Block piso = centro.getWorld().getBlockAt(x, y - 1, z);
                Block pies = centro.getWorld().getBlockAt(x, y, z);
                Block cabeza = centro.getWorld().getBlockAt(x, y + 1, z);
                if (piso.getType().isSolid() && pies.getType() == Material.AIR
                        && cabeza.getType() == Material.AIR) {
                    return new Location(centro.getWorld(), x + 0.5, y, z + 0.5);
                }
            }
        }
        return null;
    }

    private void terminarRuleta() {
        if (cierreRuleta != null) {
            cierreRuleta.cancel();
            cierreRuleta = null;
        }
        if (jugadorRuleta != null) {
            Player p = Bukkit.getPlayer(jugadorRuleta);
            if (p != null) {
                if (efectoRuleta != null) p.removePotionEffect(efectoRuleta);
                // El efecto de caida lenta solo se aplica con la levitacion.
                if (efectoRuleta == PotionEffectType.LEVITATION) {
                    p.removePotionEffect(PotionEffectType.SLOW_FALLING);
                }
                p.sendMessage("§a[Ruleta] ¡El reto ha terminado!");
            }
        }
        for (UUID id : mobsRuleta) {
            org.bukkit.entity.Entity mob = Bukkit.getEntity(id);
            if (mob != null) mob.remove();
        }
        mobsRuleta.clear();
        jugadorRuleta = null;
        efectoRuleta = null;
    }

    @Override
    public void onDisable() {
        terminarRuleta();
    }

    // Bloquea el daño entre jugadores cuando el PvP está desactivado.
    @EventHandler(
        priority = EventPriority.HIGHEST,
        ignoreCancelled = false
    )
    public void onPlayerDamage(EntityDamageByEntityEvent event) {

        if (pvpEnabled) return;

        if (!(event.getEntity() instanceof Player)) return;

        // Daño directo entre jugadores.
        if (event.getDamager() instanceof Player) {
            event.setCancelled(true);
            return;
        }

        // Daño de proyectiles lanzados por jugadores.
        if (event.getDamager() instanceof Projectile projectile) {

            ProjectileSource shooter = projectile.getShooter();

            if (shooter instanceof Player) {
                event.setCancelled(true);
            }
        }
    }

    // Obtener las vidas de un jugador.
    private int lives(UUID id) {
        return data.getInt(id.toString(), MAX_LIVES);
    }

    // Establecer las vidas de un jugador.
    private void setLives(UUID id, int amount) {

        data.set(
            id.toString(),
            Math.max(0, Math.min(MAX_LIVES, amount))
        );

        saveData();
    }

    // Guardar las vidas.
    private void saveData() {

        try {
            data.save(dataFile);

        } catch (IOException e) {
            getLogger().severe(
                "No se pudieron guardar las vidas: " + e.getMessage()
            );
        }
    }

    // Actualizar modo de juego al perder o recuperar vidas.
    private void updateGameMode(
        Player player,
        int previousLives,
        int newLives
    ) {

        if (newLives <= 0) {

            Bukkit.getScheduler().runTask(this, () -> {

                if (player.isOnline()) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            });

        } else if (previousLives <= 0) {

            Bukkit.getScheduler().runTask(this, () -> {

                if (player.isOnline()) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
            });

            player.sendMessage(
                "§a¡Has recuperado una vida y vuelves a supervivencia!"
            );
        }
    }

    // Cuando un jugador entra al servidor.
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        if (!data.contains(id.toString())) {
            setLives(id, MAX_LIVES);
        }

        if (lives(id) <= 0) {

            Bukkit.getScheduler().runTask(this, () -> {

                if (player.isOnline()) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            });

            player.sendMessage(
                "§cTe has quedado sin vidas. Estás eliminado y en modo espectador."
            );
        }
    }

    // Cuando un jugador muere por cualquier causa.
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        int remaining = Math.max(0, lives(player.getUniqueId()) - 1);
        setLives(player.getUniqueId(), remaining);

        // Titulo corto centrado, con el nombre en un subtitulo mas pequeno.
        String tableo = "§c§l¡TABLEADO!";
        String nombre = "§f" + player.getName();
        String vidas = remaining > 0
                ? "§eLe quedan §c" + remaining + " §evida(s)"
                : "§4§l¡SE HA QUEDADO SIN VIDAS!";

        // Particulas decorativas en el lugar de la muerte: no causan dano.
        Location muerte = player.getLocation().clone().add(0, 1.0, 0);
        player.getWorld().spawnParticle(Particle.EXPLOSION, muerte, 1);
        player.getWorld().spawnParticle(Particle.CLOUD, muerte, 28, 0.7, 0.6, 0.7, 0.08);
        player.getWorld().spawnParticle(Particle.SMOKE, muerte, 18, 0.6, 0.5, 0.6, 0.03);

        // El anuncio y el sonido son para todos menos el fallecido.
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            // Titulo breve centrado, nombre debajo y vidas en el chat.
            viewer.sendTitle(tableo, nombre, 10, 60, 10);
            viewer.sendMessage(vidas);
            viewer.playSound(
                    viewer.getLocation(),
                    "minecraft:entity.generic.explode",
                    3.0f,
                    1.0f
            );
        }

        if (remaining > 0) {
            player.sendMessage("§eTe quedan §c" + remaining + " §evida(s).");
        } else {
            player.sendMessage(
                    "§c¡Te has quedado sin vidas! Tras reaparecer pasarás a espectador."
            );
        }
    }

    // Cuando un jugador reaparece.
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {

        Player player = event.getPlayer();

        if (lives(player.getUniqueId()) <= 0) {

            Bukkit.getScheduler().runTask(this, () -> {

                if (
                    player.isOnline() &&
                    lives(player.getUniqueId()) <= 0
                ) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            });
        }
    }

    // Comandos del plugin.
    @Override
    public boolean onCommand(
        CommandSender sender,
        Command command,
        String label,
        String[] args
    ) {

        String cmd = command.getName().toLowerCase();

        // Comando /vidas
        if (cmd.equals("vidas")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage(
                    "Este comando es para jugadores."
                );

                return true;
            }

            sender.sendMessage(
                "§aTe quedan §c" +
                lives(player.getUniqueId()) +
                "§a/3 vidas."
            );

            return true;
        }

        // Comando /darvida
        if (cmd.equals("darvida")) {

            if (!(sender instanceof Player donor)) {

                sender.sendMessage(
                    "Solo un jugador puede donar vidas con este comando."
                );

                return true;
            }

            if (args.length != 1) {

                sender.sendMessage(
                    "§cUso: /darvida <jugador>"
                );

                return true;
            }

            Player recipient = Bukkit.getPlayerExact(args[0]);

            if (recipient == null) {

                sender.sendMessage(
                    "§cEse jugador debe estar conectado."
                );

                return true;
            }

            if (
                recipient.getUniqueId().equals(
                    donor.getUniqueId()
                )
            ) {

                sender.sendMessage(
                    "§cNo puedes darte una vida a ti mismo."
                );

                return true;
            }

            int donorLives = lives(donor.getUniqueId());
            int recipientLives = lives(recipient.getUniqueId());

            if (donorLives <= 0) {

                sender.sendMessage(
                    "§cNo tienes vidas para donar."
                );

                return true;
            }

            if (recipientLives >= MAX_LIVES) {

                sender.sendMessage(
                    "§cEse jugador ya tiene 3 vidas."
                );

                return true;
            }

            setLives(
                donor.getUniqueId(),
                donorLives - 1
            );

            setLives(
                recipient.getUniqueId(),
                recipientLives + 1
            );

            donor.sendMessage(
                "§eHas donado una vida a §b" +
                recipient.getName() +
                "§e. Te quedan §c" +
                (donorLives - 1) +
                "§e."
            );

            recipient.sendMessage(
                "§a" +
                donor.getName() +
                " te ha dado una vida. Ahora tienes §c" +
                (recipientLives + 1) +
                "§a/3."
            );

            updateGameMode(
                recipient,
                recipientLives,
                recipientLives + 1
            );

            updateGameMode(
                donor,
                donorLives,
                donorLives - 1
            );

            return true;
        }

        // Comando /vidasadmin
        if (cmd.equals("vidasadmin")) {

            if (!sender.hasPermission("extremovidas.admin")) {

                sender.sendMessage(
                    "§cNo tienes permiso para usar este comando."
                );

                return true;
            }

            if (args.length < 2) {

                sender.sendMessage("§eUso:");
                sender.sendMessage(
                    "§f/vidasadmin ver <jugador>"
                );
                sender.sendMessage(
                    "§f/vidasadmin dar <jugador> <cantidad>"
                );
                sender.sendMessage(
                    "§f/vidasadmin poner <jugador> <cantidad>"
                );

                return true;
            }

            String action = args[0].toLowerCase();
            Player target = Bukkit.getPlayerExact(args[1]);

            if (target == null) {

                sender.sendMessage(
                    "§cEl jugador debe estar conectado."
                );

                return true;
            }

            int oldLives = lives(target.getUniqueId());

            if (action.equals("ver")) {

                sender.sendMessage(
                    "§a" +
                    target.getName() +
                    " tiene §c" +
                    oldLives +
                    "§a/3 vidas."
                );

                return true;
            }

            if (
                !action.equals("dar") &&
                !action.equals("poner")
            ) {

                sender.sendMessage(
                    "§cAcción inválida. Usa ver, dar o poner."
                );

                return true;
            }

            if (args.length != 3) {

                sender.sendMessage(
                    "§cUso: /vidasadmin " +
                    action +
                    " <jugador> <cantidad>"
                );

                return true;
            }

            int amount;

            try {

                amount = Integer.parseInt(args[2]);

            } catch (NumberFormatException e) {

                sender.sendMessage(
                    "§cLa cantidad debe ser un número entero."
                );

                return true;
            }

            if (
                amount < 0 ||
                (action.equals("dar") && amount == 0)
            ) {

                sender.sendMessage(
                    "§cLa cantidad debe ser positiva."
                );

                return true;
            }

            int newLives = action.equals("dar")
                ? oldLives + amount
                : amount;

            newLives = Math.min(MAX_LIVES, newLives);

            setLives(
                target.getUniqueId(),
                newLives
            );

            updateGameMode(
                target,
                oldLives,
                newLives
            );

            sender.sendMessage(
                "§a" +
                target.getName() +
                " ahora tiene §c" +
                newLives +
                "§a/3 vidas."
            );

            target.sendMessage(
                "§eUn administrador ha actualizado tus vidas: §c" +
                newLives +
                "§e/3."
            );

            return true;
        }

        return false;
    }
}
