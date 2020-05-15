package eu.izmoqwy.parkourchallenge.model;

import eu.izmoqwy.parkourchallenge.ParkourLeaderboard;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;

import java.util.Objects;

@Getter
public class Parkour {

    @Setter
    private int databaseId;

    private String name;
    private Location spawnLocation;
    private Location startLocation;
    private Location endLocation;
    private int experience;

    @Setter
    private ParkourLeaderboard leaderboard;

    // if the @Builder is on the class itself then the constructor is protected
    @Builder
    public Parkour(int databaseId, String name, Location spawnLocation, Location startLocation, Location endLocation, int experience) {
        this.databaseId = databaseId;
        this.name = name;
        this.spawnLocation = spawnLocation;
        this.startLocation = startLocation;
        this.endLocation = endLocation;
        this.experience = experience;
    }

    // this should be used only by ParkourChallenge#addParkourToCache
    public void update(Parkour parkour) {
        this.name = parkour.getName();
        this.spawnLocation = parkour.getSpawnLocation();
        this.startLocation = parkour.getStartLocation();
        this.endLocation = parkour.getEndLocation();
        this.experience = parkour.getExperience();
    }

    public static class ParkourBuilder {

        @SuppressWarnings("FieldCanBeLocal")
        private int experience = -1;

        @Getter
        private boolean editing;

        public ParkourBuilder markAsEditing() {
            this.editing = true;
            return this;
        }

        public int getDatabaseId() {
            return databaseId;
        }

        public String getName() {
            return name;
        }

        public boolean isReady() {
            return name != null && spawnLocation != null && startLocation != null && endLocation != null && experience != -1;
        }

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Parkour)) return false;
        if (databaseId == 0) return false;

        Parkour parkour = (Parkour) o;
        return databaseId == parkour.databaseId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(databaseId);
    }

}
