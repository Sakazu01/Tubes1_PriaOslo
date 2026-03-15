package bot4;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.UnitType;

public class RobotPlayer {
    private static Entity entity;

    public static void run(RobotController rc) throws GameActionException {
        if (entity == null) {
            if (Entity.isTowerType(rc.getType())) {
                entity = new Tower(rc);
            } else {
                UnitType t = rc.getType();
                switch (t) {
                    case SOLDIER -> entity = new Soldier(rc);
                    case MOPPER -> entity = new Mopper(rc);
                    case SPLASHER -> entity = new Splasher(rc);
                    default -> entity = new Splasher(rc);
                }
            }
        }

        while (true) {
            try {
                entity.run();
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }
}
