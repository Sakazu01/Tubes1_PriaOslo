package bot3;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;


public class RobotPlayer {
    private static Entity entity;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        if (entity == null) {
            entity = Entity.isTowerType(rc.getType()) ? new Tower(rc) : new Robot(rc);
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
