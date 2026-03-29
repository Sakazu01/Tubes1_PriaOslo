package alternative_bots_1;

import battlecode.common.*;

public class Splasher {

    static void run() throws GameActionException {
        if (Globals.rc.isActionReady()) aksi();
        gerak();
        if (Globals.rc.isActionReady()) aksi();
    }

    static void aksi() throws GameActionException {
        RobotController rc = Globals.rc;
        if (!rc.isActionReady()) return;

        // cari posisi splash terbaik — utamakan cat musuh, terutama yang dekat ruin
        MapLocation bestCenter = null;
        int bestSkor = 1;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dx * dx + dy * dy > 4) continue;
                MapLocation center = new MapLocation(Globals.myLoc.x + dx, Globals.myLoc.y + dy);
                if (!rc.canAttack(center)) continue;

                int skor = 0;
                for (MapInfo s : rc.senseNearbyMapInfos(center, 4)) {
                    if (!s.isPassable()) continue;
                    MapLocation loc = s.getMapLocation();
                    PaintType cat = s.getPaint();
                    boolean innerZone = center.distanceSquaredTo(loc) <= 2;

                    if (Navigation.isEnemyPaint(cat)) {
                        if (innerZone) {
                            skor += 12;
                            for (int i = 0; i < Globals.numRuins; i++) {
                                if (loc.distanceSquaredTo(Globals.ruins[i]) <= 8) {
                                    skor += 20;
                                    break;
                                }
                            }
                        }
                    } else if (cat == PaintType.EMPTY) {
                        skor += 3;
                    } else {
                        skor -= 5;
                    }
                }

                if (skor > bestSkor) { bestSkor = skor; bestCenter = center; }
            }
        }

        if (bestCenter != null && rc.canAttack(bestCenter))
            rc.attack(bestCenter);
    }

    static void gerak() throws GameActionException {
        RobotController rc = Globals.rc;
        if (!rc.isMovementReady()) return;

        // refuel
        if (Globals.paint < 80) {
            MapLocation tower = Globals.nearestPaintTower(Globals.myLoc);
            if (tower == null) tower = Globals.nearestAllyTower(Globals.myLoc);
            if (tower != null) { Navigation.pathTo(tower); return; }
        }

        // kejar area cat musuh terdekat
        MapLocation enemyArea = null;
        int ed = Integer.MAX_VALUE;
        for (MapInfo info : Globals.nearby) {
            if (!Navigation.isEnemyPaint(info.getPaint())) continue;
            int d = Globals.myLoc.distanceSquaredTo(info.getMapLocation());
            if (d < ed) { ed = d; enemyArea = info.getMapLocation(); }
        }
        if (enemyArea != null) { Navigation.pathTo(enemyArea); return; }

        // kejar tower musuh (bukan defense)
        for (RobotInfo e : Globals.enemies) {
            if (!Globals.isTowerType(e.getType()) || Globals.isDefenseTower(e.getType())) continue;
            int dist = Globals.myLoc.distanceSquaredTo(e.getLocation());
            if (dist > 13) Navigation.moveToward(e.getLocation());
            else if (dist <= 9) Navigation.retreatFrom(e.getLocation());
            return;
        }

        Navigation.moveExpansion();
    }
}
