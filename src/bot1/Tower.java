package bot1;

import battlecode.common.*;

public class Tower extends Entity {
    final int MONEY_THRESHOLD = 1000;


    int level;
    int lastSpawned;

    // soldier is spawned first
    UnitType[] spawnOrder = {UnitType.MOPPER, UnitType.SOLDIER, UnitType.SPLASHER};

    /**
     * @brief            constructor.
     * @param rc         the entity.
     * @param level      starting level.
     */
    public Tower(RobotController rc, int level){
        super(rc);
        this.level = level;
        this.lastSpawned = 0;
    }

    @Override
    public void run() throws GameActionException {
        count++;
        
        // mindless spending
        if(rc.getMoney() > MONEY_THRESHOLD){
            // spawn a robot if there is space.
            for (Direction dir : directions){
                if (rc.canBuildRobot(spawnOrder[lastSpawned], rc.getLocation().add(dir))){
                    rc.buildRobot(spawnOrder[lastSpawned], rc.getLocation().add(dir));
                    lastSpawned = (lastSpawned + 1) % spawnOrder.length;
                    break;
                }
            }
        }
    }
}
