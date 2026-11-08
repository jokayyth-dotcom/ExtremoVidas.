package com.extremo.vidas;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.ChatColor;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collections;
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

    private File missionFile;
    private YamlConfiguration missions;
    private static final String MENU_MISION = "§5§lMisiones semanales";
    private static final String MENU_PREMIO = "§6§lElegir recompensa";
    private static final String MENU_ADMIN = "§4§lConfigurar misiones";
    private final Map<UUID, Integer> pendingClaim = new HashMap<>();
    private File dataFile;
    private YamlConfiguration data;

    private static final int MAX_LIVES = 3;

    // El PvP cambia cada 20 minutos.
    private static final long PVP_INTERVAL_TICKS = 20L * 60 * 20;

    // El PvP comienza desactivado.
    private boolean pvpEnabled = false;

    // Ruleta: un evento cada 30 minutos, con desafios de 15 minutos.
    private static final long RULETA_INTERVAL = 20L * 60 * 30;
    private static final int RULETA_MIN_DURATION = 20 * 60 * 15;
    private static final int RULETA_MAX_DURATION = 20 * 60 * 30;
    private static final long RULETA_WARNING_TICKS = 20L * 5;
    private final Random random = new Random();
    private final List<UUID> mobsRuleta = new ArrayList<>();
    private UUID jugadorRuleta;
    private PotionEffectType efectoRuleta;
    private BukkitTask cierreRuleta;
    private BukkitTask avisoRuleta;

    @Override
    public void onEnable() {

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        dataFile = new File(getDataFolder(), "vidas.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);

        missionFile = new File(getDataFolder(), "misiones.yml");
        missions = YamlConfiguration.loadConfiguration(missionFile);
        comprobarSemana();
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

    // La ruleta avisa primero y aplica el desafio cinco segundos despues.
    private void iniciarRuleta() {
        terminarRuleta();
        List<Player> candidatos = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SURVIVAL && !p.isDead()
                    && lives(p.getUniqueId()) > 0) candidatos.add(p);
        }
        if (candidatos.isEmpty()) {
            Bukkit.broadcastMessage("§7[Ruleta] No hay jugadores elegibles.");
            return;
        }
        Player elegido = candidatos.get(random.nextInt(candidatos.size()));
        jugadorRuleta = elegido.getUniqueId();
        int categoria = random.nextInt(4); // efectos, mobs comunes, ocultos o teleport
        int duracion = RULETA_MIN_DURATION + random.nextInt(RULETA_MAX_DURATION - RULETA_MIN_DURATION + 1);
        int nivel = 1 + random.nextInt(3);
        String reto;
        int variante = random.nextInt(7);
        EntityType mob = null;
        int cantidad = 0;
        if (categoria == 0) {
            String[] nombres = {"Ceguera", "Lentitud", "Debilidad", "Fatiga minera", "Hambre", "Veneno", "Levitacion"};
            reto = nombres[variante] + " nivel " + (variante == 0 ? 1 : nivel)
                    + " durante " + (duracion / 1200) + " minutos aprox.";
        } else if (categoria == 1) {
            EntityType[] tipos = {EntityType.WITHER_SKELETON, EntityType.BLAZE,
                    EntityType.SPIDER, EntityType.SKELETON, EntityType.CAVE_SPIDER};
            mob = tipos[random.nextInt(tipos.length)];
            cantidad = 20 + random.nextInt(11);
            reto = cantidad + " " + mob.name().replace('_', ' ');
        } else if (categoria == 2) {
            if (random.nextBoolean()) {
                mob = EntityType.ILLUSIONER;
                cantidad = 5 + random.nextInt(11);
            } else {
                mob = EntityType.GIANT;
                cantidad = 1 + random.nextInt(5);
            }
            reto = cantidad + " " + mob.name().replace('_', ' ');
        } else {
            reto = random.nextBoolean() ? "TELETRANSPORTE A 20.000 BLOQUES" : "TELETRANSPORTE A Y=250";
        }
        final EntityType mobElegido = mob;
        final int cantidadElegida = cantidad;
        final String retoElegido = reto;
        Bukkit.broadcastMessage("§5§l[RULETA] §f" + elegido.getName() + " §crecibira: §e" + reto
                + " §7(en 5 segundos)");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§5§l¡RULETA!", "§e" + elegido.getName() + " §7- §c5 segundos", 5, 70, 5);
            p.playSound(p.getLocation(), "minecraft:item.goat_horn.sound.0", 1.0f, 1.0f);
        }
        avisoRuleta = Bukkit.getScheduler().runTaskLater(this, () -> {
            avisoRuleta = null;
            if (!elegido.isOnline() || elegido.isDead() || elegido.getGameMode() != GameMode.SURVIVAL
                    || lives(elegido.getUniqueId()) <= 0) {
                Bukkit.broadcastMessage("§7[Ruleta] Reto cancelado: el jugador ya no es elegible.");
                jugadorRuleta = null;
                return;
            }
            Bukkit.broadcastMessage("§4§l[RULETA] §c¡DESAFIO ACTIVADO! §f" + elegido.getName()
                    + ": §e" + retoElegido);
            if (categoria == 0) {
                PotionEffectType[] efectos = {PotionEffectType.BLINDNESS, PotionEffectType.SLOWNESS,
                        PotionEffectType.WEAKNESS, PotionEffectType.MINING_FATIGUE,
                        PotionEffectType.HUNGER, PotionEffectType.POISON, PotionEffectType.LEVITATION};
                PotionEffectType tipo = efectos[variante];
                // La ceguera no tiene niveles funcionales distintos en Minecraft.
                int amplificador = tipo == PotionEffectType.BLINDNESS ? 0 : nivel - 1;
                elegido.addPotionEffect(new PotionEffect(tipo, duracion, amplificador));
                efectoRuleta = tipo;
                elegido.sendMessage("§c[Ruleta] Efecto activo durante " + (duracion / 20) + " segundos.");
            } else if (categoria == 1 || categoria == 2) {
                int creados = 0;
                Location centro = elegido.getLocation();
                for (int i = 0; i < cantidadElegida; i++) {
                    Location spawn = buscarSpawnSeguro(centro);
                    if (spawn == null) continue;
                    try {
                        LivingEntity entidad = (LivingEntity) centro.getWorld().spawnEntity(spawn, mobElegido);
                        entidad.setRemoveWhenFarAway(false);
                        mobsRuleta.add(entidad.getUniqueId());
                        creados++;
                    } catch (IllegalArgumentException ex) {
                        getLogger().warning("No se pudo generar " + mobElegido + ": " + ex.getMessage());
                        break;
                    }
                }
                elegido.sendMessage("§c[Ruleta] Aparecieron " + creados + " enemigos. Se retiraran en "
                        + (duracion / 1200) + " minutos aproximadamente.");
            } else {
                boolean lejos = retoElegido.contains("20.000");
                teletransportarRuleta(elegido, lejos);
            }
            cierreRuleta = Bukkit.getScheduler().runTaskLater(this, this::terminarRuleta, duracion);
        }, RULETA_WARNING_TICKS);
    }

    private void teletransportarRuleta(Player p, boolean lejos) {
        Location origen = p.getLocation();
        org.bukkit.World mundo = origen.getWorld();
        if (mundo == null) return;
        Location destino;
        if (lejos) {
            double angulo = random.nextDouble() * Math.PI * 2;
            double x = origen.getX() + Math.cos(angulo) * 20000;
            double z = origen.getZ() + Math.sin(angulo) * 20000;
            org.bukkit.WorldBorder borde = mundo.getWorldBorder();
            double mitad = borde.getSize() / 2.0 - 16;
            if (mitad <= 0) { p.sendMessage("§c[Ruleta] Borde demasiado pequeno para teleport."); return; }
            x = Math.max(borde.getCenter().getX() - mitad, Math.min(borde.getCenter().getX() + mitad, x));
            z = Math.max(borde.getCenter().getZ() - mitad, Math.min(borde.getCenter().getZ() + mitad, z));
            final double fx = x, fz = z;
            // Paper carga el chunk de destino sin bloquear el hilo principal.
            mundo.getChunkAtAsync((int)Math.floor(fx) >> 4, (int)Math.floor(fz) >> 4).thenAccept(chunk ->
                Bukkit.getScheduler().runTask(this, () -> {
                    if (!p.isOnline() || p.isDead()) return;
                    int y = mundo.getHighestBlockYAt((int)Math.floor(fx), (int)Math.floor(fz)) + 2;
                    y = Math.min(mundo.getMaxHeight() - 3, Math.max(mundo.getMinHeight() + 3, y));
                    Location sitio = new Location(mundo, fx, y, fz, origen.getYaw(), origen.getPitch());
                    p.teleport(sitio);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 45, 0));
                    p.sendMessage("§c[Ruleta] Teletransportado lejos de tu posicion original.");
                })
            );
        } else {
            int y = Math.min(250, mundo.getMaxHeight() - 3);
            if (y <= mundo.getMinHeight() + 2) return;
            destino = new Location(mundo, origen.getX(), y, origen.getZ(), origen.getYaw(), origen.getPitch());
            p.teleport(destino);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 45, 0));
            p.sendMessage("§c[Ruleta] ¡Estas en la altura Y=" + y + "! Tienes 45 segundos de caida lenta.");
        }
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
        if (avisoRuleta != null) { avisoRuleta.cancel(); avisoRuleta = null; }
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
        guardarMisiones();
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
        if (cmd.equals("mision")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cAbre este menú dentro del juego.");
                return true;
            }
            comprobarSemana();
            abrirMisiones(player);
            return true;
        }
        if (cmd.equals("misionadmin")) {
            return comandoMisionAdmin(sender, args);
        }


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

    // ================= MISIONES SEMANALES =================
    // Hasta 3 misiones compartidas, progreso y premios individuales.
    private String semanaActual() {
        LocalDate hoy = LocalDate.now();
        WeekFields wf = WeekFields.ISO;
        return hoy.get(wf.weekBasedYear()) + "-" + hoy.get(wf.weekOfWeekBasedYear());
    }

    private void comprobarSemana() {
        String actual = semanaActual();
        if (!actual.equals(missions.getString("semana", ""))) {
            missions.set("semana", actual);
            missions.set("jugadores", null);
            guardarMisiones();
        }
    }

    private void guardarMisiones() {
        try { missions.save(missionFile); }
        catch (IOException e) { getLogger().severe("No se guardaron las misiones: " + e.getMessage()); }
    }

    private String base(int n) { return "misiones." + n + "."; }
    private String progreso(Player p, int n) {
        return "jugadores." + p.getUniqueId() + "." + n + ".";
    }
    private boolean existe(int n) { return missions.getBoolean(base(n) + "activa", false); }
    private int objetivo(int n) { return missions.getInt(base(n) + "cantidad", 1); }
    private int avance(Player p, int n) {
        return missions.getInt(progreso(p, n) + "avance", 0);
    }
    private boolean cobrada(Player p, int n) {
        return missions.getBoolean(progreso(p, n) + "cobrada", false);
    }
    private String descripcion(int n) {
        String tipo = missions.getString(base(n) + "tipo", "");
        String id = missions.getString(base(n) + "objetivo", "");
        String desc = (tipo.equals("matar") ? "Mata " : "Entrega ") + objetivo(n) + " " + id;
        if (tipo.equals("entregar") && missions.contains(base(n) + "extra.item"))
            desc += " + " + missions.getInt(base(n) + "extra.cantidad") + " " + missions.getString(base(n) + "extra.item");
        return desc;
    }

    private ItemStack icono(Material material, String nombre, String... descripcion) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(nombre);
        meta.setLore(Arrays.asList(descripcion));
        item.setItemMeta(meta);
        return item;
    }

    private void abrirMisiones(Player p) {
        comprobarSemana();
        Inventory inv = Bukkit.createInventory(null, 27, MENU_MISION);
        for (int n = 1; n <= 3; n++) {
            if (!existe(n)) continue;
            String estado = cobrada(p,n) ? "§aYa cobrada esta semana" :
                "§eProgreso: " + avance(p,n) + "/" + objetivo(n);
            inv.setItem(10 + (n - 1) * 3,
                icono(Material.WRITABLE_BOOK, "§d§lMisión " + n,
                    "§f" + descripcion(n), estado,
                    "§7Clic para entregar o reclamar"));
        }
        if (p.hasPermission("extremovidas.admin"))
            inv.setItem(22, icono(Material.COMMAND_BLOCK, "§cAdministrar", "§7Clic para configurar"));
        p.openInventory(inv);
    }

    private void abrirAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, MENU_ADMIN);
        for (int n = 1; n <= 3; n++) {
            inv.setItem(10 + (n - 1) * 3,
                icono(Material.ANVIL, "§eMisión " + n,
                    existe(n) ? "§f" + descripcion(n) : "§7Sin configurar",
                    "§7Configura con /misionadmin", "§7La recompensa es el objeto en tu mano"));
        }
        inv.setItem(22, icono(Material.BOOK, "§aComandos en el chat",
            "§f/misionadmin matar <1-3> <mob> <cantidad>",
            "§f/misionadmin entregar <1-3> <item> <cantidad>",
            "§f/misionadmin extra <1-3> <item> <cantidad>",
            "§f/misionadmin premio <1-3>"));
        p.openInventory(inv);
    }

    private boolean comandoMisionAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("extremovidas.admin")) {
            sender.sendMessage("§cNo tienes permiso."); return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player p) abrirAdmin(p);
            else sender.sendMessage("Usa /misionadmin matar|entregar|premio|borrar ...");
            return true;
        }
        if (args.length < 2) { sender.sendMessage("§eUso: /misionadmin <matar|entregar|premio|borrar> <1-3> [mob/item] [cantidad]"); return true; }
        int n;
        try { n = Integer.parseInt(args[1]); }
        catch (NumberFormatException ex) { sender.sendMessage("§cNúmero inválido."); return true; }
        if (n < 1 || n > 3) { sender.sendMessage("§cElige misión 1, 2 o 3."); return true; }
        String b = base(n);
        String accion = args[0].toLowerCase(Locale.ROOT);
        if (accion.equals("borrar")) {
            missions.set("misiones." + n, null);
        } else if (accion.equals("premio")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("§cDebes estar dentro del juego."); return true; }
            if (!existe(n)) { sender.sendMessage("§cConfigura primero la misión."); return true; }
            ItemStack mano = p.getInventory().getItemInMainHand();
            if (mano.getType().isAir()) { sender.sendMessage("§cSujeta el premio en la mano."); return true; }
            missions.set(b + "premio", mano.clone());
        } else if (accion.equals("extra")) {
            if (args.length != 4 || !"entregar".equals(missions.getString(b + "tipo"))) {
                sender.sendMessage("§cUso: /misionadmin extra <1-3> <item> <cantidad> (solo entrega)"); return true;
            }
            Material mat = Material.matchMaterial(args[2]);
            int cantidad;
            try { cantidad = Integer.parseInt(args[3]); }
            catch (NumberFormatException ex) { sender.sendMessage("§cCantidad inválida."); return true; }
            if (mat == null || !mat.isItem() || mat.isAir() || cantidad < 1 || cantidad > 100000) {
                sender.sendMessage("§cItem o cantidad inválidos."); return true;
            }
            missions.set(b + "extra.item", mat.name());
            missions.set(b + "extra.cantidad", cantidad);
            missions.set("jugadores", null);
        } else if (accion.equals("matar") || accion.equals("entregar")) {
            if (args.length != 4) { sender.sendMessage("§cUso: /misionadmin " + accion + " <1-3> <mob/item> <cantidad>"); return true; }
            String id = args[2].toUpperCase(Locale.ROOT);
            if (accion.equals("matar")) {
                try { EntityType tipo = EntityType.valueOf(id); if (!tipo.isAlive() || tipo == EntityType.PLAYER) throw new IllegalArgumentException(); }
                catch (IllegalArgumentException ex) { sender.sendMessage("§cMob inválido, ejemplo: WARDEN, ZOMBIE, BLAZE."); return true; }
            } else {
                Material mat = Material.matchMaterial(id);
                if (mat == null || mat.isAir() || !mat.isItem()) { sender.sendMessage("§cÍtem inválido, ejemplo: TOTEM_OF_UNDYING o DIAMOND."); return true; }
                id = mat.name();
            }
            int cantidad;
            try { cantidad = Integer.parseInt(args[3]); }
            catch (NumberFormatException ex) { sender.sendMessage("§cCantidad inválida."); return true; }
            if (cantidad < 1 || cantidad > 100000) { sender.sendMessage("§cCantidad entre 1 y 100000."); return true; }
            missions.set(b + "tipo", accion);
            missions.set(b + "objetivo", id);
            missions.set(b + "cantidad", cantidad);
            missions.set(b + "activa", true);
            missions.set(b + "extra", null);
            // Modificar un objetivo reinicia su progreso para evitar reclamar el nuevo con avances antiguos.
            missions.set("jugadores", null);
        } else {
            sender.sendMessage("§cAcción inválida."); return true;
        }
        guardarMisiones();
        sender.sendMessage("§aMisión " + n + " actualizada correctamente.");
        if (sender instanceof Player p) abrirAdmin(p);
        return true;
    }

    @EventHandler
    public void onMisionKill(EntityDeathEvent event) {
        Player p = event.getEntity().getKiller();
        if (p == null) return;
        comprobarSemana();
        for (int n = 1; n <= 3; n++) {
            if (!existe(n) || cobrada(p,n)) continue;
            if (!"matar".equals(missions.getString(base(n) + "tipo"))) continue;
            if (!event.getEntityType().name().equals(missions.getString(base(n) + "objetivo"))) continue;
            int anterior = avance(p,n);
            if (anterior >= objetivo(n)) continue;
            missions.set(progreso(p,n) + "avance", anterior + 1);
            if (anterior + 1 == objetivo(n)) p.sendMessage("§a¡Misión " + n + " completada! Usa /mision para reclamar.");
        }
        guardarMisiones();
    }

    private int contar(Player p, Material mat) {
        int cantidad = 0;
        for (ItemStack stack : p.getInventory().getStorageContents())
            if (stack != null && stack.getType() == mat) cantidad += stack.getAmount();
        return cantidad;
    }

    private void consumir(Player p, Material mat, int cantidad) {
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && cantidad > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != mat) continue;
            int quitar = Math.min(cantidad, stack.getAmount());
            stack.setAmount(stack.getAmount() - quitar);
            if (stack.getAmount() <= 0) contents[i] = null;
            cantidad -= quitar;
        }
        p.getInventory().setStorageContents(contents);
    }

    private void entregar(Player p, int n) {
        Material mat = Material.matchMaterial(missions.getString(base(n) + "objetivo", ""));
        if (mat == null) return;
        int falta = objetivo(n) - avance(p,n);
        if (falta <= 0) return;
        Material extra = Material.matchMaterial(missions.getString(base(n) + "extra.item", ""));
        int cantidadExtra = extra == null ? 0 : missions.getInt(base(n) + "extra.cantidad", 0);
        int totalPrincipal = falta + (mat == extra ? cantidadExtra : 0);
        if (contar(p,mat) < totalPrincipal || (extra != null && extra != mat && contar(p,extra) < cantidadExtra)) {
            p.sendMessage("§cNecesitas " + totalPrincipal + " " + mat.name() +
                (extra == null ? "" : " y " + cantidadExtra + " " + extra.name()) + " en tu inventario.");
            return;
        }
        // Entrega atómica: primero se comprueban todos los materiales.
        consumir(p,mat,totalPrincipal);
        if (extra != null && extra != mat) consumir(p,extra,cantidadExtra);
        missions.set(progreso(p,n) + "avance", objetivo(n));
        guardarMisiones();
        p.sendMessage("§a¡Entrega realizada! Elige tu recompensa.");
    }

    private void abrirPremios(Player p, int n) {
        pendingClaim.put(p.getUniqueId(), n);
        Inventory inv = Bukkit.createInventory(null, 27, MENU_PREMIO);
        inv.setItem(11, icono(Material.RED_DYE, "§c+1 VIDA", "§7Máximo 3 vidas", "§7Excluye el premio de objetos"));
        ItemStack premio = missions.getItemStack(base(n) + "premio");
        if (premio == null) inv.setItem(15, icono(Material.BARRIER, "§cSin premio configurado"));
        else {
            ItemStack mostrar = premio.clone();
            ItemMeta meta = mostrar.getItemMeta();
            if (meta != null) {
                java.util.List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("§eClic para elegir premio en vez de vida");
                meta.setLore(lore); mostrar.setItemMeta(meta);
            }
            inv.setItem(15, mostrar);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onMissionClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        // Impide cobrar progreso de una semana anterior si el menu quedo abierto.
        comprobarSemana();
        String titulo = event.getView().getTitle();
        if (!titulo.equals(MENU_MISION) && !titulo.equals(MENU_PREMIO) && !titulo.equals(MENU_ADMIN)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        int slot = event.getRawSlot();
        if (titulo.equals(MENU_ADMIN)) {
            if (slot == 22 || slot == 10 || slot == 13 || slot == 16) {
                p.sendMessage("§eConfigura las misiones con /misionadmin matar, entregar, extra y premio.");
                p.sendMessage("§7Ejemplo: /misionadmin matar 1 WARDEN 3");
            }
            return;
        }
        if (titulo.equals(MENU_MISION)) {
            if (slot == 22 && p.hasPermission("extremovidas.admin")) { abrirAdmin(p); return; }
            int n = slot == 10 ? 1 : slot == 13 ? 2 : slot == 16 ? 3 : -1;
            if (n < 0 || !existe(n)) return;
            if (cobrada(p,n)) { p.sendMessage("§cYa reclamaste esta misión esta semana."); return; }
            if ("entregar".equals(missions.getString(base(n) + "tipo")) && avance(p,n) < objetivo(n)) entregar(p,n);
            if (avance(p,n) < objetivo(n)) { p.sendMessage("§eAún no completas la misión."); return; }
            abrirPremios(p,n);
            return;
        }
        if (titulo.equals(MENU_PREMIO) && (slot == 11 || slot == 15)) {
            Integer n = pendingClaim.get(p.getUniqueId());
            if (n == null || !existe(n) || cobrada(p,n) || avance(p,n) < objetivo(n)) return;
            if (slot == 11) {
                int antes = lives(p.getUniqueId());
                if (antes >= MAX_LIVES) { p.sendMessage("§cYa tienes 3 vidas. Elige el premio de objetos."); return; }
                setLives(p.getUniqueId(), antes + 1);
                updateGameMode(p, antes, antes + 1);
                p.sendMessage("§a¡Has ganado 1 vida! Ahora tienes " + (antes + 1) + "/3.");
            } else {
                ItemStack premio = missions.getItemStack(base(n) + "premio");
                if (premio == null) { p.sendMessage("§cEl administrador no configuró un premio."); return; }
                Map<Integer, ItemStack> sobrantes = p.getInventory().addItem(premio.clone());
                for (ItemStack restante : sobrantes.values())
                    p.getWorld().dropItemNaturally(p.getLocation(), restante);
                p.sendMessage("§a¡Recompensa semanal entregada!");
            }
            missions.set(progreso(p,n) + "cobrada", true);
            guardarMisiones();
            pendingClaim.remove(p.getUniqueId());
            p.closeInventory();
        }
    }

    @EventHandler
    public void onMissionQuit(PlayerQuitEvent event) {
        pendingClaim.remove(event.getPlayer().getUniqueId());
    }

}
