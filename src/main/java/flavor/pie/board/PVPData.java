package flavor.pie.board;

import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataHolder;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.manipulator.immutable.common.AbstractImmutableData;
import org.spongepowered.api.data.manipulator.mutable.common.AbstractData;
import org.spongepowered.api.data.merge.MergeFunction;
import org.spongepowered.api.data.persistence.AbstractDataBuilder;
import org.spongepowered.api.data.persistence.InvalidDataException;

import java.util.NoSuchElementException;
import java.util.Optional;

public class PVPData extends AbstractData<PVPData, PVPData.ImmutablePVPData> {
    Board board;
    int kills;
    int deaths;
    int killstreak;
    int mobKills;
    private PVPData(int kills, int deaths, int killstreak, int mobKills, Board board) {
        registerGettersAndSetters();
        this.board = board;
        this.kills = kills;
        this.deaths = deaths;
        this.killstreak = killstreak;
        this.mobKills = mobKills;
    }

    @Override
    protected void registerGettersAndSetters() {
        registerFieldGetter(board.deaths, this::getDeaths);
        registerFieldSetter(board.deaths, this::setDeaths);
        registerFieldGetter(board.kills, this::getKills);
        registerFieldSetter(board.kills, this::setKills);
        registerFieldGetter(board.killstreak, this::getKillstreak);
        registerFieldSetter(board.killstreak, this::setKillstreak);
        registerFieldGetter(board.mobKills, this::getMobKills);
        registerFieldSetter(board.mobKills, this::setMobKills);
    }
    public int getKills() {
        return kills;
    }
    public void setKills(int kills) {
        this.kills = kills;
    }
    public int getDeaths() {
        return deaths;
    }
    public void setDeaths(int deaths) {
        this.deaths = deaths;
    }
    public int getKillstreak() {
        return killstreak;
    }
    public void setKillstreak(int killstreak) {
        this.killstreak = killstreak;
    }
    public int getMobKills() {
        return mobKills;
    }
    public void setMobKills(int mobKills) {
        this.mobKills = mobKills;
    }
    @Override
    public Optional<PVPData> fill(DataHolder dataHolder, MergeFunction overlap) {
        if (dataHolder.supports(PVPData.class)) {
            PVPData data = dataHolder.get(PVPData.class).get();
            mobKills = data.getMobKills();
            kills = data.getKills();
            killstreak = data.getKillstreak();
            deaths = data.getDeaths();
            return Optional.of(this);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<PVPData> from(DataContainer container) {
        try {
            return Optional.of(board.builder.build(container).get());
        } catch (NoSuchElementException | InvalidDataException e) {
            return Optional.empty();
        }
    }

    @Override
    public PVPData copy() {
        return new PVPData(kills, deaths, killstreak, mobKills, board);
    }

    @Override
    public ImmutablePVPData asImmutable() {
        return new ImmutablePVPData(kills, deaths, killstreak, mobKills, board);
    }

    @Override
    public int compareTo(PVPData o) {
        return kills - o.kills;
    }

    @Override
    public int getContentVersion() {
        return 1;
    }

    public static class ImmutablePVPData extends AbstractImmutableData<ImmutablePVPData, PVPData> {
         int kills;
         int deaths;
         int killstreak;
         int mobKills;
         Board board;

        ImmutablePVPData(int kills, int deaths, int killstreak, int mobKills, Board board) {
            this.kills = kills;
            this.deaths = deaths;
            this.killstreak = killstreak;
            this.mobKills = mobKills;
            this.board = board;
            registerGetters();
        }
        public int getKills() {
            return kills;
        }
        public int getDeaths() {
            return deaths;
        }
        public int getKillstreak( ){
            return killstreak;
        }
        public int getMobKills() {
            return mobKills;
        }
        @Override
        protected void registerGetters() {
            registerFieldGetter(board.mobKills, this::getMobKills);
            registerFieldGetter(board.deaths, this::getDeaths);
            registerFieldGetter(board.kills, this::getKills);
            registerFieldGetter(board.killstreak, this::getKillstreak);
        }

        @Override
        public PVPData asMutable() {
            return new PVPData(kills, deaths, killstreak, mobKills, board);
        }

        @Override
        public int compareTo(ImmutablePVPData o) {
            return kills - o.kills;
        }

        @Override
        public int getContentVersion() {
            return 1;
        }
    }
    public static class Builder extends AbstractDataBuilder<PVPData> {
        int kills;
        int killstreak;
        int mobKills;
        int deaths;
        Board board;
        Builder(Board board) {
            super(PVPData.class, 0);
            this.board = board;
            kills = 0;
            killstreak = 0;
            mobKills = 0;
            deaths = 0;
        }

        @Override
        protected Optional<PVPData> buildContent(DataView container) throws InvalidDataException {
            try {
                return Optional.of(new PVPData(container.getInt(board.kills.getQuery()).get(), container.getInt(board.deaths.getQuery()).get(), container.getInt(board.killstreak.getQuery()).get(), container.getInt(board.mobKills.getQuery()).get(), board));
            } catch (NoSuchElementException e) {
                throw new InvalidDataException(e);
            }
        }
    }
}
