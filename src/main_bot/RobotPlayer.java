package main_bot;

import battlecode.common.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;



public class RobotPlayer {
    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * It is like the main function for your robot. If this method returns, the robot dies!
     *
     * @param rc  The RobotController object. You use it to perform actions from this robot, and to get
     *            information on its current status. Essentially your portal to interacting with the world.
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        
        Entity bot;
        switch(rc.getType()){
            case SOLDIER: bot = new Soldier(rc); break;
            case MOPPER: bot = new Mopper(rc); break;
            case SPLASHER: bot = new Splasher(rc); break;

            case LEVEL_ONE_DEFENSE_TOWER: bot = new TowerDefense(rc, 1); break;
            case LEVEL_TWO_DEFENSE_TOWER: bot = new TowerDefense(rc, 2); break;
            case LEVEL_THREE_DEFENSE_TOWER: bot = new TowerDefense(rc, 3); break;

            case LEVEL_ONE_MONEY_TOWER: bot = new TowerMoney(rc, 1); break;
            case LEVEL_TWO_MONEY_TOWER: bot = new TowerMoney(rc, 2); break;
            case LEVEL_THREE_MONEY_TOWER: bot = new TowerMoney(rc, 3); break;

            case LEVEL_ONE_PAINT_TOWER: bot = new TowerPaint(rc, 1); break;
            case LEVEL_TWO_PAINT_TOWER: bot = new TowerPaint(rc, 2); break;
            case LEVEL_THREE_PAINT_TOWER: bot = new TowerPaint(rc, 3); break;

            default: throw new IllegalArgumentException("Invalid robot type: " + rc.getType());
        }

        while(true){
            bot.run();
        }
    }
}
