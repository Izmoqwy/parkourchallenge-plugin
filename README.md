# ParkourChallenge Plugin

**THIS PLUGIN IS NOT MEANT TO BE USED (EVEN IF IT WORKS) AND IF YOU DO, NO SUPPORT WILL BE PROVIDED**

I’m not planning to maintain this plugin at all. If you need an actual parkour plugin you should check out resources like [https://www.spigotmc.org/resources/parkour.23685/](https://www.spigotmc.org/resources/parkour.23685/).

# Behaviors

## Database operations and async

The database connection has the auto-commit disabled. Meaning that we can execute queries in the main thread and then we can **commit in async**. Select queries are also async.
An auto-commit task is scheduled to run every 10 minutes to save attempts and players. When a parkour is created or edited a commit will be triggered.

Note: Queries are executed in the main thread only when saving a parkour so we can tell the player if the parkour has been successfully saved or not. Otherwise, the queries are executed async.

The leaderboards are not cached when the plugin loads but when they are queried for the first time.

## Location check

### Start location

The start location check is handled by a workaround that stores all the locations within 2 blocks' radius for each start location (~= for each parkour map).

Why? If we do a simple distance check without storing this, we have to iterate through all the parkours to get the start location and its distance between the player when iterating through all the players. A nested loop like this may slow down the plugin/server.

### End location

When the player is playing on a parkour map, we check the distance between the parkour’s end location and him instead of checking the start locations.

## Notes

-   The selection menu is the same shared bukkit inventory for all players.
-   When a player completes a parkour, he gets the parkour XP even if he already completed it before.

## Further improvements

As said on the top, this plugin isn’t meant to be used as an actual parkour plugin. However, this could be partially solved with the following improvements:

-   Configuring messages
-   Parkour sync: when a parkour is edited, all the players playing it get “kicked out the run” and have to restart
-   Parkour deletion (requires Parkour sync)
-   Disable inventory clicks cancellation & configure the selection item
-   Add an option to set the block we want the parkour to have in the selection menu
-   Checkpoints or at least being able to split a parkour into multiple sections
