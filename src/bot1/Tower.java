package bot1;

import battlecode.common.*;

public class Tower extends Entity {
    int level;
    int lastSpawned;

    // soldier is spawned first
    UnitType[] spawnOrder = {UnitType.MOPPER, UnitType.SOLDIER, UnitType.SPLASHER};

    public Tower(RobotController rc, int level){
        super(rc);
        this.level = level;
        this.lastSpawned = 0;
    }

    @Override
    public void run() throws GameActionException {
        count++;
        
        // spawn a robot if there is space.

        if(rc.getMoney() > 500){
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
