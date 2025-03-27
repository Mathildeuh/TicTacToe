package fr.mathilde.ticTacToe;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class TicTacToe extends JavaPlugin implements Listener, CommandExecutor {
    // Game configuration constants
    private static final int[] GAME_SLOTS = {3, 4, 5, 12, 13, 14, 21, 22, 23};
    private static final int INVENTORY_SIZE = 27;
    private static final ChatColor[] NAME_COLORS = {
            ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN, ChatColor.GOLD,
            ChatColor.LIGHT_PURPLE, ChatColor.AQUA, ChatColor.YELLOW
    };

    // Assuming this is in a Bukkit/Spigot plugin class
    private final NamespacedKey TOTAL_GAMES_KEY = new NamespacedKey(this, "tictactoe_total_games");
    private final NamespacedKey WINS_KEY = new NamespacedKey(this, "tictactoe_wins");
    private final NamespacedKey DRAWS_KEY = new NamespacedKey(this, "tictactoe_draws");

    // Game state variables
    private final Map<Player, Player> pendingChallenges = new HashMap<>();
    private final Random random = new Random();
    private Inventory gameInventory;
    private Player player1;
    private Player player2;
    private boolean[][] player1Moves;
    private boolean[][] player2Moves;
    private boolean isPlayer1Turn;
    private boolean gameInProgress;
    private ChatColor player1Color;
    private ChatColor player2Color;

    @Override
    public void onEnable() {
        Objects.requireNonNull(getCommand("tictactoe")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check for stats command
        if (args.length == 1 && args[0].equalsIgnoreCase("stats")) {
            return handleStatsCommand(sender);
        }

        // Check for force game (for ops)
        if (args.length == 2 && (sender.isOp() || sender.hasPermission("tictactoe.force"))) {
            return handleForceGame(sender, args);
        }

        // Validate sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent utiliser cette commande.");
            return true;
        }

        Player challenger = (Player) sender;

        // Handle no arguments (check pending challenges)
        if (args.length == 0) {
            return handlePendingChallenge(challenger);
        }

        // Validate challenge command
        if (args.length != 1) {
            challenger.sendMessage(ChatColor.RED + "Utilisation : /tictactoe <joueur> [force]");
            return true;
        }

        return processChallenge(challenger, args[0]);
    }

    private boolean handleStatsCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent voir leurs statistiques.");
            return true;
        }

        Player player = (Player) sender;
        PersistentDataContainer dataContainer = player.getPersistentDataContainer();

        int totalGames = dataContainer.getOrDefault(TOTAL_GAMES_KEY, PersistentDataType.INTEGER, 0);
        int wins = dataContainer.getOrDefault(WINS_KEY, PersistentDataType.INTEGER, 0);
        int draws = dataContainer.getOrDefault(DRAWS_KEY, PersistentDataType.INTEGER, 0);
        int losses = totalGames - wins - draws;

        double winPercentage = totalGames > 0 ? (wins * 100.0 / totalGames) : 0;

        sender.sendMessage(ChatColor.GOLD + "--- Statistiques Tic-Tac-Toe ---");
        sender.sendMessage(ChatColor.GREEN + "Parties jouées : " + totalGames);
        sender.sendMessage(ChatColor.GREEN + "Victoires : " + wins);
        sender.sendMessage(ChatColor.YELLOW + "Matchs nuls : " + draws);
        sender.sendMessage(ChatColor.RED + "Défaites : " + losses);
        sender.sendMessage(ChatColor.AQUA + "Pourcentage de victoire : " + String.format("%.2f", winPercentage) + "%");

        return true;
    }

    private boolean handleForceGame(CommandSender sender, String[] args) {
        Player[] players = new Player[2];
        for (int i = 0; i < 2; i++) {
            players[i] = Bukkit.getPlayer(args[i]);
            if (players[i] == null || !players[i].isOnline()) {
                sender.sendMessage(ChatColor.RED + "Le joueur " + args[i] + " n'est pas en ligne.");
                return true;
            }
        }

        if (players[0].equals(players[1])) {
            sender.sendMessage(ChatColor.RED + "Un joueur ne peut pas jouer contre lui-même !");
            return true;
        }

        if (sender instanceof Player) {
            sender.sendMessage(ChatColor.GREEN + "Partie forcée entre " +
                    players[0].getName() + " et " + players[1].getName() + " !");
        }

        initializeGame(players[0], players[1]);
        return true;
    }

    private boolean handlePendingChallenge(Player challenger) {
        for (Map.Entry<Player, Player> challenge : pendingChallenges.entrySet()) {
            if (challenge.getValue().equals(challenger)) {
                Player challengerOriginal = challenge.getKey();
                pendingChallenges.remove(challengerOriginal);
                initializeGame(challengerOriginal, challenger);
                return true;
            }
        }

        challenger.sendMessage(ChatColor.RED + "Utilisez /tictactoe <joueur> pour défier quelqu'un.");
        return true;
    }

    private boolean processChallenge(Player challenger, String challengedName) {
        Player challenged = Bukkit.getPlayer(challengedName);

        if (challenged == null || !challenged.isOnline()) {
            challenger.sendMessage(ChatColor.RED + "Le joueur " + challengedName + " n'est pas en ligne.");
            return true;
        }

        if (challenger.equals(challenged)) {
            challenger.sendMessage(ChatColor.RED + "Vous ne pouvez pas vous défier vous-même.");
            return true;
        }

        ChatColor challengerColor = getUniqueColor();
        ChatColor challengedColor = getUniqueColor(challengerColor);

        pendingChallenges.put(challenger, challenged);

        challenger.sendMessage(challengerColor + "Vous avez défié " + challengedColor + challenged.getName() + ChatColor.GREEN + " au Tic-Tac-Toe !");
        challenged.sendMessage(challengedColor + challenger.getName() + ChatColor.GREEN + " vous a défié au Tic-Tac-Toe ! Tapez /tictactoe pour accepter.");

        challenger.playSound(challenger.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        challenged.playSound(challenged.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);

        return true;
    }

    private void initializeGame(Player challenger, Player challenged) {
        player1Color = getUniqueColor();
        player2Color = getUniqueColor(player1Color);

        gameInventory = Bukkit.createInventory(null, INVENTORY_SIZE,
                player1Color + challenger.getName() + ChatColor.WHITE +
                        " vs " + player2Color + challenged.getName());

        player1 = challenger;
        player2 = challenged;
        player1Moves = new boolean[3][3];
        player2Moves = new boolean[3][3];
        isPlayer1Turn = true;
        gameInProgress = true;

        gameInventory.setItem(0, createPlayerHead(player1, player1Color));
        gameInventory.setItem(8, createPlayerHead(player2, player2Color));
        gameInventory.setItem(26, createQuitButton());

        for (int slot : GAME_SLOTS) {
            gameInventory.setItem(slot, createItemFrame());
        }

        player1.openInventory(gameInventory);
        player2.openInventory(gameInventory);

        player1.sendMessage(player1Color + "La partie commence ! C'est votre tour.");
        player2.sendMessage(player1Color + challenger.getName() + ChatColor.YELLOW + " commence la partie.");

        player1.playSound(player1.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
        player2.playSound(player2.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
    }

    private ItemStack createQuitButton() {
        ItemStack quitButton = new ItemStack(Material.BARRIER);
        ItemMeta meta = quitButton.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "Abandonner");
        quitButton.setItemMeta(meta);
        return quitButton;
    }

    private ItemStack createPlayerHead(Player player, ChatColor color) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        skullMeta.setOwningPlayer(player);
        skullMeta.setDisplayName(color + player.getName());
        skull.setItemMeta(skullMeta);
        return skull;
    }

    private ItemStack createItemFrame() {
        ItemStack itemFrame = new ItemStack(Material.ITEM_FRAME);
        ItemMeta meta = itemFrame.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "Cliquez pour jouer");
        itemFrame.setItemMeta(meta);
        return itemFrame;
    }

    private ChatColor getUniqueColor() {
        return NAME_COLORS[random.nextInt(NAME_COLORS.length)];
    }

    private ChatColor getUniqueColor(ChatColor excludeColor) {
        ChatColor newColor;
        do {
            newColor = getUniqueColor();
        } while (newColor == excludeColor);
        return newColor;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(gameInventory)) return;
        event.setCancelled(true);
        if (!gameInProgress) return;

        Player currentPlayer = (Player) event.getWhoClicked();
        Player expectedPlayer = isPlayer1Turn ? player1 : player2;
        ChatColor currentColor = isPlayer1Turn ? player1Color : player2Color;

        // Handle quit button
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.BARRIER) {
            Player quitter = (Player) event.getWhoClicked();
            Player winner = quitter.equals(player1) ? player2 : player1;
            endGame(winner, false);
            return;
        }

        // Validate current player's turn
        if (!currentPlayer.equals(expectedPlayer)) {
            currentPlayer.sendMessage(ChatColor.RED + "Ce n'est pas votre tour !");
            currentPlayer.playSound(currentPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
            return;
        }

        int slot = event.getRawSlot();
        int gridIndex = findGameSlotIndex(slot);

        if (gridIndex == -1) return;

        int gridRow = gridIndex / 3;
        int gridCol = gridIndex % 3;

        // Check if slot is already occupied
        if (player1Moves[gridRow][gridCol] || player2Moves[gridRow][gridCol]) {
            currentPlayer.sendMessage(ChatColor.RED + "Cette case est déjà occupée !");
            currentPlayer.playSound(currentPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
            return;
        }

        currentPlayer.playSound(currentPlayer.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);

        Material colorMaterial = isPlayer1Turn ? Material.RED_TERRACOTTA : Material.BLUE_TERRACOTTA;
        ItemStack coloredPane = new ItemStack(colorMaterial);
        ItemMeta meta = coloredPane.getItemMeta();
        meta.setDisplayName(currentColor + currentPlayer.getName());
        coloredPane.setItemMeta(meta);

        gameInventory.setItem(slot, coloredPane);

        // Update game state
        if (isPlayer1Turn) {
            player1Moves[gridRow][gridCol] = true;
        } else {
            player2Moves[gridRow][gridCol] = true;
        }

        // Check win condition
        if (checkWin(isPlayer1Turn ? player1Moves : player2Moves)) {
            endGame(currentPlayer, true);
            return;
        }

        // Check draw condition
        if (checkDraw()) {
            endGameDraw();
            return;
        }

        // Switch turns
        switchTurns();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (gameInProgress && event.getInventory().equals(gameInventory)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Player player = (Player) event.getPlayer();
                player.openInventory(gameInventory);
                player.sendMessage(ChatColor.RED + "Vous ne pouvez pas quitter la partie en cours !");
            }, 1L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player disconnectedPlayer = event.getPlayer();
        if (gameInProgress && (disconnectedPlayer.equals(player1) || disconnectedPlayer.equals(player2))) {
            Player winner = disconnectedPlayer.equals(player1) ? player2 : player1;
            endGameDisconnect(winner);
        }
    }

    private int findGameSlotIndex(int slot) {
        for (int i = 0; i < GAME_SLOTS.length; i++) {
            if (GAME_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private void switchTurns() {
        isPlayer1Turn = !isPlayer1Turn;
        Player nextPlayer = isPlayer1Turn ? player1 : player2;
        Player waitingPlayer = isPlayer1Turn ? player2 : player1;
        ChatColor nextColor = isPlayer1Turn ? player1Color : player2Color;

        nextPlayer.playSound(nextPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.2f);
        waitingPlayer.playSound(waitingPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);

        player1.sendMessage(ChatColor.YELLOW + (isPlayer1Turn ? "C'est votre tour !" : "Tour de " + nextColor + nextPlayer.getName()));
        player2.sendMessage(ChatColor.YELLOW + (isPlayer1Turn ? nextColor + nextPlayer.getName() + ChatColor.YELLOW + " joue" : "C'est votre tour !"));
    }

    private boolean checkWin(boolean[][] playerMoves) {
        // Check rows and columns
        for (int i = 0; i < 3; i++) {
            if ((playerMoves[i][0] && playerMoves[i][1] && playerMoves[i][2]) ||
                    (playerMoves[0][i] && playerMoves[1][i] && playerMoves[2][i])) {
                return true;
            }
        }

        // Check diagonals
        return (playerMoves[0][0] && playerMoves[1][1] && playerMoves[2][2]) ||
                (playerMoves[0][2] && playerMoves[1][1] && playerMoves[2][0]);
    }

    private boolean checkDraw() {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (!player1Moves[row][col] && !player2Moves[row][col]) {
                    return false;
                }
            }
        }
        return true;
    }

    private void recordGameStats(Player winner, boolean isDraw) {
        updatePlayerStats(winner, !isDraw, isDraw);
        Player loser = winner.equals(player1) ? player2 : player1;
        updatePlayerStats(loser, false, isDraw);
    }

    private void updatePlayerStats(Player player, boolean isWin, boolean isDraw) {
        PersistentDataContainer dataContainer = player.getPersistentDataContainer();

        int totalGames = dataContainer.getOrDefault(TOTAL_GAMES_KEY, PersistentDataType.INTEGER, 0) + 1;
        dataContainer.set(TOTAL_GAMES_KEY, PersistentDataType.INTEGER, totalGames);

        if (isDraw) {
            int draws = dataContainer.getOrDefault(DRAWS_KEY, PersistentDataType.INTEGER, 0) + 1;
            dataContainer.set(DRAWS_KEY, PersistentDataType.INTEGER, draws);
        } else if (isWin) {
            int wins = dataContainer.getOrDefault(WINS_KEY, PersistentDataType.INTEGER, 0) + 1;
            dataContainer.set(WINS_KEY, PersistentDataType.INTEGER, wins);
        }
    }

    private void endGame(Player winner, boolean isWin) {
        gameInProgress = false;

        recordGameStats(winner, !isWin);

        ChatColor winnerColor = winner.equals(player1) ? player1Color : player2Color;

        winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        Player loser = winner.equals(player1) ? player2 : player1;
        loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

        blinkWinningLine(winner);

        new BukkitRunnable() {
            @Override
            public void run() {
                spawnCreeperFirework(winner);

                player1.sendMessage(ChatColor.GREEN + "Le joueur " + winnerColor + winner.getName() + ChatColor.GREEN + " a gagné la partie de Tic-Tac-Toe !");
                player2.sendMessage(ChatColor.GREEN + "Le joueur " + winnerColor + winner.getName() + ChatColor.GREEN + " a gagné la partie de Tic-Tac-Toe !");
                player1.closeInventory();
                player2.closeInventory();
            }
        }.runTaskLater(this, 40);
    }

    private void endGameDraw() {
        gameInProgress = false;

        recordGameStats(player1, true);

        player1.playSound(player1.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        player2.playSound(player2.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);

        player1.sendMessage(ChatColor.YELLOW + "La partie de Tic-Tac-Toe se termine par un match nul !");
        player2.sendMessage(ChatColor.YELLOW + "La partie de Tic-Tac-Toe se termine par un match nul !");
        player1.closeInventory();
        player2.closeInventory();
    }

    private void endGameDisconnect(Player winner) {
        gameInProgress = false;

        recordGameStats(winner, false);

        ChatColor winnerColor = winner.equals(player1) ? player1Color : player2Color;

        winner.sendMessage(ChatColor.GREEN + "Vous avez gagné car votre adversaire a quitté la partie !");
        winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        winner.sendMessage(ChatColor.YELLOW + "Le joueur " + winnerColor + winner.getName() + ChatColor.YELLOW +
                " a gagné la partie de Tic-Tac-Toe par forfait (partie forcée) !");
    }

    private void blinkWinningLine(Player winner) {
        boolean[][] winnerMoves = winner.equals(player1) ? player1Moves : player2Moves;
        int[] winningSlots = findWinningLineSlots(winnerMoves);

        if (winningSlots != null) {
            new BukkitRunnable() {
                int count = 0;

                @Override
                public void run() {
                    if (count < 6) {
                        for (int slot : winningSlots) {
                            gameInventory.setItem(slot, count % 2 == 0 ?
                                    new ItemStack(Material.GLOWSTONE) :
                                    new ItemStack(winner.equals(player1) ? Material.RED_TERRACOTTA : Material.BLUE_TERRACOTTA));
                        }
                        count++;
                    } else {
                        cancel();
                    }
                }
            }.runTaskTimer(this, 0, 10);
        }
    }

    private int[] findWinningLineSlots(boolean[][] playerMoves) {
        // Check rows
        for (int row = 0; row < 3; row++) {
            if (playerMoves[row][0] && playerMoves[row][1] && playerMoves[row][2]) {
                return new int[]{
                        GAME_SLOTS[row * 3],
                        GAME_SLOTS[row * 3 + 1],
                        GAME_SLOTS[row * 3 + 2]
                };
            }
        }

        // Check columns
        for (int col = 0; col < 3; col++) {
            if (playerMoves[0][col] && playerMoves[1][col] && playerMoves[2][col]) {
                return new int[]{
                        GAME_SLOTS[col],
                        GAME_SLOTS[col + 3],
                        GAME_SLOTS[col + 6]
                };
            }
        }

        // Check diagonals
        if (playerMoves[0][0] && playerMoves[1][1] && playerMoves[2][2]) {
            return new int[]{GAME_SLOTS[0], GAME_SLOTS[4], GAME_SLOTS[8]};
        }
        if (playerMoves[0][2] && playerMoves[1][1] && playerMoves[2][0]) {
            return new int[]{GAME_SLOTS[2], GAME_SLOTS[4], GAME_SLOTS[6]};
        }

        return null;
    }

    private void spawnCreeperFirework(Player winner) {
        Location loc = winner.getLocation();
        Firework fw = loc.getWorld().spawn(loc.add(0, 2, 0), Firework.class);
        FireworkMeta fwMeta = fw.getFireworkMeta();

        fwMeta.addEffect(FireworkEffect.builder()
                .withColor(Color.GREEN)
                .withFade(Color.LIME)
                .with(FireworkEffect.Type.CREEPER)
                .trail(true)
                .flicker(true)
                .build());

        fwMeta.setPower(0);
        fw.setFireworkMeta(fwMeta);
    }
}