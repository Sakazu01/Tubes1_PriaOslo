package bot2;

import battlecode.common.*;

public strictfp class RobotPlayer {
    public static void run(RobotController rc) throws GameActionException {
        Globals.init(rc);
        while (true) {
            try {
                Globals.updatePerTurn();
                if (Globals.isTower) {
                    Tower.run();
                } else {
                    switch (rc.getType()) {
                        case SOLDIER:  Soldier.run();  break;
                        case SPLASHER: Splasher.run(); break;
                        case MOPPER:   Mopper.run();   break;
                        default: break;
                    }
                }
            } catch (GameActionException e) {
                System.out.println("GAE: " + e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("EX: " + e.getMessage());
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }
}
