package flavor.pie.board;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.primitives.Ints;
import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.slf4j.Logger;
import org.spongepowered.api.CatalogTypes;
import org.spongepowered.api.Game;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.key.Key;
import org.spongepowered.api.data.key.KeyFactory;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.value.mutable.Value;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.scoreboard.Scoreboard;
import org.spongepowered.api.scoreboard.critieria.Criteria;
import org.spongepowered.api.scoreboard.displayslot.DisplaySlots;
import org.spongepowered.api.scoreboard.objective.Objective;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.EconomyService;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.text.Text;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.MonthDay;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@Plugin(id="board",name="Board",description="A Sponge remake of ScoreboardStats.",authors="pie_flavor",version="1.0-SNAPSHOT")
public class Board {
    public Key<Value<Integer>> kills;
    public Key<Value<Integer>> deaths;
    public Key<Value<Integer>> killstreak;
    public Key<Value<Integer>> mobKills;
    public PVPData.Builder builder;
    final long MB = 1024*1024;
    @Inject
    Game game;
    @Inject
    Logger logger;
    @Inject
    @DefaultConfig(sharedRoot = false)
    ConfigurationLoader<CommentedConfigurationNode> loader;
    CommentedConfigurationNode root;
    BiMap<Text, String> vars;
    Task task = null;
    @Listener
    public void preInit(GamePreInitializationEvent e) {
        vars = HashBiMap.create();
        try {
            root = loader.load();
            if (root.getNode("version").isVirtual()) {
                loader.save(HoconConfigurationLoader.builder().setURL(game.getAssetManager().getAsset(this, "default.conf").get().getUrl()).build().load());
                root = loader.load();
            }
            for (CommentedConfigurationNode node : root.getNode("scoreboard", "items").getChildrenList()) {
                try {
                    vars.put(node.getNode("title").getValue(TypeToken.of(Text.class)), node.getNode("value").getString());
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (IOException | IllegalStateException | ObjectMappingException ex) {
            logger.error("Could not load config properly! Disabling.");
            ex.printStackTrace();
            disable();
        }
    }
    private void disable() {
        game.getEventManager().unregisterPluginListeners(this);
        game.getCommandManager().getOwnedBy(this).forEach(game.getCommandManager()::removeMapping);
    }
    @Listener
    public void init(GameInitializationEvent e) {
        kills = KeyFactory.makeSingleKey(Integer.class, Value.class, DataQuery.of("kills"));
        deaths = KeyFactory.makeSingleKey(Integer.class, Value.class, DataQuery.of("deaths"));
        killstreak = KeyFactory.makeSingleKey(Integer.class, Value.class, DataQuery.of("killstreak"));
        mobKills = KeyFactory.makeSingleKey(Integer.class, Value.class, DataQuery.of("mobKills"));
        builder = new PVPData.Builder(this);
        game.getDataManager().registerBuilder(PVPData.class, builder);
        game.getCommandManager().register(this, CommandSpec.builder().description(Text.of("Toggles the scoreboard sidebar.")).executor(this::toggle).build(), "sidebar", "side", "board", "sb");
    }
    @Listener
    public void startServer(GameStartedServerEvent e) {
        if (task == null) {
            task = Task.builder().intervalTicks(root.getNode("scoreboard", "update-delay").getInt()).execute(this::updateVariables).name("board-S-ScoreboardUpdater").submit(this);
            if (root.getNode("temp-scoreboard-enabled").getBoolean()) {
                int show = root.getNode("temp-scoreboard", "interval-show").getInt();
                int hide = root.getNode("temp-scoreboard", "interval-hide").getInt();
                Task.builder().delayTicks(show+hide).intervalTicks(show+hide).execute(() -> game.getServer().getOnlinePlayers().forEach(this::swapMain));
                Task.builder().delayTicks(show).intervalTicks(show+hide).execute(() -> game.getServer().getOnlinePlayers().forEach(this::swapAlt));
            }
        }
    }
    public void updateVariables() {
        for (Player p : game.getServer().getOnlinePlayers()) {
            Scoreboard board = p.getScoreboard();
            Objective main = board.getObjective("main").get();
            for (Map.Entry<Text, String> entry : vars.entrySet()) {
                main.getOrCreateScore(entry.getKey()).setScore(parseKey(entry.getValue(), p));
            }
            Objective alt = board.getObjective("alt").get();
            String key = root.getNode("temp-scoreboard", "type").getString();
            String color = root.getNode("temp-scoreboard", "color").getString();
            for (Player p2 : game.getServer().getOnlinePlayers()) {
                alt.getOrCreateScore(Text.of(game.getRegistry().getType(CatalogTypes.TEXT_COLOR, color), p2.getName())).setScore(parseKey(key, p2));
            }
        }
    }
    public void swapMain(Player p) {
        Scoreboard board = p.getScoreboard();
        if (board.getObjective(DisplaySlots.SIDEBAR).isPresent()) {
            board.updateDisplaySlot(board.getObjective("main").get(), DisplaySlots.SIDEBAR);
        }
    }
    public void swapAlt(Player p) {
        Scoreboard board = p.getScoreboard();
        if (board.getObjective(DisplaySlots.SIDEBAR).isPresent()) {
            board.updateDisplaySlot(board.getObjective("alt").get(), DisplaySlots.SIDEBAR);
        }
    }
    @Listener
    public void onJoin(ClientConnectionEvent.Join e) throws ObjectMappingException {
        Task.builder().delayTicks(1).execute(() -> {try {e.getTargetEntity().setScoreboard(constructScoreboard(e.getTargetEntity()));}catch(Exception ignored){}});
    }
    public CommandResult toggle(CommandSource src, CommandContext args) throws CommandException {
        if (!(src instanceof Player)) {
            throw new CommandException(Text.of("You must be a player!"));
        }
        Player p = (Player) src;
        Scoreboard board = p.getScoreboard();
        if (board.getObjective(DisplaySlots.SIDEBAR).isPresent()) {
            board.clearSlot(DisplaySlots.SIDEBAR);
        } else {
            try {
                board.updateDisplaySlot(board.getObjective(root.getNode("scoreboard", "title").getValue(TypeToken.of(Text.class)).toPlain()).get(), DisplaySlots.SIDEBAR);
            } catch (ObjectMappingException | NoSuchElementException e) {
                throw new CommandException(Text.of("Unknown error"), e);
            }
        }
        return CommandResult.success();
    }
    int parseKey(String s, User user) {
        String[] sections = s.split(":");
        String key = sections[0];
        switch (key) {
            case "tps":
                return (int) game.getServer().getTicksPerSecond();
            case "ping":
                if (user.getPlayer().isPresent()) {
                    return user.getPlayer().get().getConnection().getLatency();
                } else {
                    return -1;
                }
            case "online":
                return game.getServer().getOnlinePlayers().size();
            case "free_ram":
                return Ints.saturatedCast(Runtime.getRuntime().freeMemory() / MB);
            case "used_ram_percent":
                return Ints.saturatedCast(((long) (1-((float) Runtime.getRuntime().freeMemory() / (float) Runtime.getRuntime().maxMemory()))*100) / MB);
            case "max_ram":
                return Ints.saturatedCast(Runtime.getRuntime().maxMemory()/ MB);
            case "used_ram":
                return Ints.saturatedCast((Runtime.getRuntime().maxMemory() - Runtime.getRuntime().freeMemory())/ MB);
            case "day":
                return MonthDay.now().getDayOfMonth();
            case "month":
                return MonthDay.now().getMonthValue();
            case "week_day":
                return DayOfWeek.from(MonthDay.now()).getValue();
            case "helmet":
                return user.getHelmet().map(stack -> stack.get(Keys.ITEM_DURABILITY).orElse(0)).orElse(0);
            case "chestplate":
                return user.getChestplate().map(stack -> stack.get(Keys.ITEM_DURABILITY).orElse(0)).orElse(0);
            case "leggings":
                return user.getLeggings().map(stack -> stack.get(Keys.ITEM_DURABILITY).orElse(0)).orElse(0);
            case "boots":
                return user.getBoots().map(stack -> stack.get(Keys.ITEM_DURABILITY).orElse(0)).orElse(0);
            case "money":
                Optional<EconomyService> economyService_ = game.getServiceManager().provide(EconomyService.class);
                if (!economyService_.isPresent()) {
                    return 0;
                }
                EconomyService service = economyService_.get();
                if (sections.length > 0 && !sections[1].equals("")) {
                    return Ints.saturatedCast(service.getOrCreateAccount(user.getUniqueId()).get().getBalance(game.getRegistry().getType(Currency.class, sections[1]).orElse(service.getDefaultCurrency())).longValue());
                } else {
                    return Ints.saturatedCast(service.getOrCreateAccount(user.getUniqueId()).get().getBalance(service.getDefaultCurrency()).longValue());
                }
            case "literal":
                if (sections.length > 0 && !sections[1].equals("")) {
                    try {
                        return Integer.parseInt(sections[1]);
                    } catch (NumberFormatException ex) {
                        return 0;
                    }
                } else {
                    return 0;
                }
            case "kills":
                return user.getOrCreate(PVPData.class).get().kills;
            case "deaths":
                return user.getOrCreate(PVPData.class).get().deaths;
            case "kdr":
                return user.getOrCreate(PVPData.class).get().kills / user.getOrCreate(PVPData.class).get().deaths;
            case "mob":
                return user.getOrCreate(PVPData.class).get().mobKills;
            case "killstreak":
                return user.getOrCreate(PVPData.class).get().killstreak;
            default:
                return 0;
        }
    }
    Scoreboard constructScoreboard(User user) throws ObjectMappingException {
        Scoreboard board = Scoreboard.builder().build();
        CommentedConfigurationNode node = root.getNode("scoreboard");
        Objective main = Objective.builder().criterion(Criteria.DUMMY).name("main").displayName(node.getNode("title").getValue(TypeToken.of(Text.class))).build();
        board.addObjective(main);
        for (BiMap.Entry<Text, String> entry : vars.entrySet()) {
            main.getOrCreateScore(entry.getKey()).setScore(parseKey(entry.getValue(), user));
        }
        node = root.getNode("temp-scoreboard");
        Objective alt = Objective.builder().criterion(Criteria.DUMMY).name("alt").displayName(node.getNode("title").getValue(TypeToken.of(Text.class))).build();
        board.addObjective(alt);
        UserStorageService service = game.getServiceManager().provide(UserStorageService.class).get();
        for (GameProfile profile : service.getAll()) {
            Optional<User> user_ = service.get(profile);
            if (user_.isPresent()) {
                alt.getOrCreateScore(Text.of(node.getNode("color"), user_.get().getName())).setScore(parseKey(node.getNode("type").getString(), user_.get()));
            }
        }
        return board;
    }
}
