package greedybot;

import battlecode.common.*;
import java.util.Random;

public class Globals {

    public static RobotController rc;
    public static MapLocation myLoc;
    public static int roundNum, paint, money;
    public static Team myTeam, enemyTeam;
    public static UnitType myType;
    public static boolean isTower;
    public static int myID;
    public static Random rng;

    public static int mapWidth, mapHeight;
    public static MapLocation mapCenter;

    public static RobotInfo[] nearbyAllies, nearbyEnemies;
    public static MapInfo[] nearbyMapInfos;

    public static final int EARLY_GAME_END = 300;
    public static final int SRP_TOWER_THRESHOLD = 6;
    public static final int MID_GAME_END = 1000;

    // tower yang pernah keliatan, 30 harusnya cukup
    public static MapLocation[] knownPaintTowers = new MapLocation[30];
    public static int numKnownPaintTowers = 0;
    public static MapLocation[] knownRuins = new MapLocation[50];
    public static int numKnownRuins = 0;
    public static MapLocation[] knownMoneyTowers = new MapLocation[30];
    public static int numKnownMoneyTowers = 0;
    public static int totalTowers = 0;

    public static void init(RobotController controller) {
        rc = controller;
        rng = new Random(rc.getID());
        mapWidth = rc.getMapWidth();
        mapHeight = rc.getMapHeight();
        mapCenter = new MapLocation(mapWidth / 2, mapHeight / 2);
        myType = rc.getType();
        myTeam = rc.getTeam();
        enemyTeam = myTeam.opponent();
        myID = rc.getID();
        isTower = isTowerType(myType);
    }

    public static void updatePerTurn() throws GameActionException {
        myLoc = rc.getLocation();
        roundNum = rc.getRoundNum();
        paint = rc.getPaint();
        money = rc.getMoney();
        myType = rc.getType();
        nearbyMapInfos = rc.senseNearbyMapInfos();
        nearbyAllies = rc.senseNearbyRobots(-1, myTeam);
        nearbyEnemies = rc.senseNearbyRobots(-1, enemyTeam);

        // scan ruin dan tower ally di sekitar
        for (MapInfo info : nearbyMapInfos) {
            if (!info.hasRuin()) continue;
            MapLocation r = info.getMapLocation();
            boolean ada = false;
            for (int i = 0; i < numKnownRuins; i++) if (knownRuins[i].equals(r)) { ada = true; break; }
            if (!ada && numKnownRuins < knownRuins.length) knownRuins[numKnownRuins++] = r;
        }
        for (RobotInfo ally : nearbyAllies) {
            if (!isTowerType(ally.getType())) continue;
            MapLocation loc = ally.getLocation();
            if (isPaintTower(ally.getType())) {
                boolean ada = false;
                for (int i = 0; i < numKnownPaintTowers; i++) if (knownPaintTowers[i].equals(loc)) { ada = true; break; }
                if (!ada && numKnownPaintTowers < knownPaintTowers.length) knownPaintTowers[numKnownPaintTowers++] = loc;
            } else if (isMoneyTower(ally.getType())) {
                boolean ada = false;
                for (int i = 0; i < numKnownMoneyTowers; i++) if (knownMoneyTowers[i].equals(loc)) { ada = true; break; }
                if (!ada && numKnownMoneyTowers < knownMoneyTowers.length) knownMoneyTowers[numKnownMoneyTowers++] = loc;
            }
        }

        // baca pesan dari tower (broadcast lokasi tower)
        // buffer simpan 5 ronde terakhir, jadi baca dari roundNum-4 biar ga kelewat
        Message[] msgs = rc.readMessages(Math.max(1, roundNum - 4));
        for (Message msg : msgs) {
            int[] decoded = Tower.decodeMessage(msg.getBytes());
            MapLocation loc = new MapLocation(decoded[1], decoded[2]);
            if (decoded[0] == 0) {
                boolean ada = false;
                for (int i = 0; i < numKnownMoneyTowers; i++) if (knownMoneyTowers[i].equals(loc)) { ada = true; break; }
                if (!ada && numKnownMoneyTowers < knownMoneyTowers.length) knownMoneyTowers[numKnownMoneyTowers++] = loc;
            } else if (decoded[0] == 1) {
                boolean ada = false;
                for (int i = 0; i < numKnownPaintTowers; i++) if (knownPaintTowers[i].equals(loc)) { ada = true; break; }
                if (!ada && numKnownPaintTowers < knownPaintTowers.length) knownPaintTowers[numKnownPaintTowers++] = loc;
            }
        }
        totalTowers = numKnownPaintTowers + numKnownMoneyTowers;
    }

    public static boolean isTowerType(UnitType t)    { return t.name().contains("TOWER"); }
    public static boolean isPaintTower(UnitType t)   { return t.name().contains("PAINT_TOWER"); }
    public static boolean isMoneyTower(UnitType t)   { return t.name().contains("MONEY"); }
    public static boolean isDefenseTower(UnitType t) { return t.name().contains("DEFENSE"); }

    public static MapLocation nearestPaintTower(MapLocation from) {
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (int i = 0; i < numKnownPaintTowers; i++) {
            int d = from.distanceSquaredTo(knownPaintTowers[i]);
            if (d < bd) { bd = d; best = knownPaintTowers[i]; }
        }
        return best;
    }

    public static MapLocation nearestAllyTower(MapLocation from) {
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (RobotInfo ally : nearbyAllies) {
            if (!isTowerType(ally.getType())) continue;
            int d = from.distanceSquaredTo(ally.getLocation());
            if (d < bd) { bd = d; best = ally.getLocation(); }
        }
        for (int i = 0; i < numKnownPaintTowers; i++) {
            int d = from.distanceSquaredTo(knownPaintTowers[i]);
            if (d < bd) { bd = d; best = knownPaintTowers[i]; }
        }
        for (int i = 0; i < numKnownMoneyTowers; i++) {
            int d = from.distanceSquaredTo(knownMoneyTowers[i]);
            if (d < bd) { bd = d; best = knownMoneyTowers[i]; }
        }
        return best;
    }

    // 0=rotasi, 1=mirror horizontal, 2=mirror vertikal
    // bisa di-update dari Soldier kalau explore target ternyata salah
    public static int symmetryType = 0;

    public static MapLocation predictEnemyLocation(MapLocation loc) {
        if (symmetryType == 1) return new MapLocation(loc.x, mapHeight - 1 - loc.y);
        if (symmetryType == 2) return new MapLocation(mapWidth - 1 - loc.x, loc.y);
        return new MapLocation(mapWidth - 1 - loc.x, mapHeight - 1 - loc.y); // rotasi
    }
}
