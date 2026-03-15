package greedymax;

import battlecode.common.*;
import java.util.Random;

public class Globals {

    static RobotController rc;
    static MapLocation myLoc, center;
    static RobotInfo[] allies, enemies;
    static MapInfo[] nearby;
    static int round, paint, mapW, mapH;
    static Team myTeam, enemyTeam;
    static UnitType myType;
    static boolean isTower;
    static Random rng;

    static MapLocation[] allyTowers = new MapLocation[25];
    static int[] allyTowerType = new int[25]; // 0=money, 1=paint, 2=defense
    static int numTowers = 0;
    static MapLocation[] ruins = new MapLocation[40];
    static int numRuins = 0;
    // ruin yang udah diklaim soldier lain — jangan rebutan
    static MapLocation[] claimedRuins = new MapLocation[20];
    static int numClaimed = 0;
    static int symType = 0;

    static void init(RobotController controller) {
        rc = controller;
        rng = new Random(rc.getID());
        mapW = rc.getMapWidth();
        mapH = rc.getMapHeight();
        center = new MapLocation(mapW / 2, mapH / 2);
        myType = rc.getType();
        myTeam = rc.getTeam();
        enemyTeam = myTeam.opponent();
        isTower = isTowerType(myType);
    }

    static void updatePerTurn() throws GameActionException {
        myLoc = rc.getLocation();
        round = rc.getRoundNum();
        paint = rc.getPaint();
        myType = rc.getType();
        nearby = rc.senseNearbyMapInfos();
        allies = rc.senseNearbyRobots(-1, myTeam);
        enemies = rc.senseNearbyRobots(-1, enemyTeam);

        for (MapInfo info : nearby) {
            if (!info.hasRuin()) continue;
            MapLocation r = info.getMapLocation();
            boolean ada = false;
            for (int i = 0; i < numRuins; i++) if (ruins[i].equals(r)) { ada = true; break; }
            if (!ada && numRuins < ruins.length) ruins[numRuins++] = r;
        }

        for (RobotInfo a : allies) {
            if (!isTowerType(a.getType())) continue;
            MapLocation loc = a.getLocation();
            boolean ada = false;
            for (int i = 0; i < numTowers; i++) if (allyTowers[i].equals(loc)) { ada = true; break; }
            if (!ada && numTowers < allyTowers.length) {
                allyTowers[numTowers] = loc;
                allyTowerType[numTowers] = isPaintTower(a.getType()) ? 1 : isDefenseTower(a.getType()) ? 2 : 0;
                numTowers++;
            }
        }

        Message[] msgs = rc.readMessages(Math.max(1, round - 4));
        for (Message msg : msgs) {
            int raw = msg.getBytes();
            int tipe = (raw >> 12) & 0xF;
            int x = (raw >> 6) & 0x3F;
            int y = raw & 0x3F;
            MapLocation loc = new MapLocation(x, y);
            if (tipe == 3) {
                // klaim ruin dari soldier lain
                boolean ada = false;
                for (int i = 0; i < numClaimed; i++) if (claimedRuins[i].equals(loc)) { ada = true; break; }
                if (!ada && numClaimed < claimedRuins.length) claimedRuins[numClaimed++] = loc;
            } else {
                boolean ada = false;
                for (int i = 0; i < numTowers; i++) if (allyTowers[i].equals(loc)) { ada = true; break; }
                if (!ada && numTowers < allyTowers.length) {
                    allyTowers[numTowers] = loc;
                    allyTowerType[numTowers] = tipe;
                    numTowers++;
                }
            }
        }
    }

    static boolean isRuinClaimed(MapLocation ruin) {
        for (int i = 0; i < numClaimed; i++) if (claimedRuins[i].equals(ruin)) return true;
        return false;
    }

    static boolean isTowerType(UnitType t) { return t.name().contains("TOWER"); }
    static boolean isPaintTower(UnitType t) { return t.name().contains("PAINT_TOWER"); }
    static boolean isDefenseTower(UnitType t) { return t.name().contains("DEFENSE"); }

    static int countTowerType(int tipe) {
        int cnt = 0;
        for (int i = 0; i < numTowers; i++) if (allyTowerType[i] == tipe) cnt++;
        return cnt;
    }

    static MapLocation firstMoneyTower() {
        for (int i = 0; i < numTowers; i++)
            if (allyTowerType[i] == 0) return allyTowers[i];
        return null;
    }

    static MapLocation nearestPaintTower(MapLocation from) {
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (int i = 0; i < numTowers; i++) {
            if (allyTowerType[i] != 1) continue;
            int d = from.distanceSquaredTo(allyTowers[i]);
            if (d < bd) { bd = d; best = allyTowers[i]; }
        }
        return best;
    }

    static MapLocation nearestAllyTower(MapLocation from) {
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (RobotInfo a : allies) {
            if (!isTowerType(a.getType())) continue;
            int d = from.distanceSquaredTo(a.getLocation());
            if (d < bd) { bd = d; best = a.getLocation(); }
        }
        for (int i = 0; i < numTowers; i++) {
            int d = from.distanceSquaredTo(allyTowers[i]);
            if (d < bd) { bd = d; best = allyTowers[i]; }
        }
        return best;
    }

    static MapLocation predictEnemy(MapLocation loc) {
        if (symType == 1) return new MapLocation(loc.x, mapH - 1 - loc.y);
        if (symType == 2) return new MapLocation(mapW - 1 - loc.x, loc.y);
        return new MapLocation(mapW - 1 - loc.x, mapH - 1 - loc.y);
    }
}
