package eu.izmoqwy.parkourchallenge.database;

import com.google.common.base.Preconditions;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.Arrays;
import java.util.UUID;

public class DatabaseSerializer {

    private DatabaseSerializer() {
        throw new UnsupportedOperationException();
    }

    public static String locationToString(Location location) {
        Preconditions.checkNotNull(location);
        Preconditions.checkNotNull(location.getWorld());

        return String.join(";", Arrays.asList(
                location.getWorld().getUID().toString(),
                doubleToString(location.getX()),
                doubleToString(location.getY()),
                doubleToString(location.getZ()),
                doubleToString(location.getYaw()),
                doubleToString(location.getPitch())
        ));
    }

    public static Location stringToLocation(String locationString) {
        Preconditions.checkNotNull(locationString);

        String[] locationArgs = locationString.split(";");
        Preconditions.checkArgument(locationArgs.length == 6);

        return new Location(
                Bukkit.getWorld(UUID.fromString(locationArgs[0])),
                stringToDouble(locationArgs[1]),
                stringToDouble(locationArgs[2]),
                stringToDouble(locationArgs[3]),
                (float) stringToDouble(locationArgs[4]),
                (float) stringToDouble(locationArgs[5])
        );
    }

    private static String doubleToString(double position) {
        return String.valueOf(Math.floor(position * 100) / 100);
    }

    private static double stringToDouble(String positionString) {
        return Double.parseDouble(positionString);
    }

}
