package bot2;

import battlecode.common.*;

public class Splasher {

    static MapLocation rushTarget = null;
    static int rushAge = 0;

    static void run() throws GameActionException {
        if (Globals.rc.isActionReady()) aksi();
        gerak();
        if (Globals.rc.isActionReady()) aksi();
    }

    static void aksi() throws GameActionException {
        RobotController rc = Globals.rc;
        if (!rc.isActionReady() || Globals.paint < 50) return;

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

        MapLocation target = null;
        int best = -1;

        int urgency = Math.max(0, (150 - Globals.paint) * 20);
        if (urgency > best) {
            MapLocation tower = Globals.nearestPaintTower(Globals.myLoc);
            if (tower == null) tower = Globals.nearestAllyTower(Globals.myLoc);
            if (tower != null) {
                int s = urgency - Globals.myLoc.distanceSquaredTo(tower);
                if (s > best) { best = s; target = tower; }
            }
        }

        // kalo nemu banyak cat musuh, serbu langsung
        if (Globals.paint >= 100) {
            int enemyTiles = 0;
            MapLocation enemyCenter = null;
            for (MapInfo info : Globals.nearby) {
                if (Navigation.isEnemyPaint(info.getPaint())) {
                    enemyTiles++;
                    enemyCenter = info.getMapLocation();
                }
            }
            if (enemyTiles >= 5 && enemyCenter != null) {
                rushTarget = enemyCenter;
                rushAge = 0;
            }
        }

        if (rushTarget != null) {
            rushAge++;
            if (rushAge > 15) rushTarget = null;
            else {
                int s = 5000 - Globals.myLoc.distanceSquaredTo(rushTarget);
                if (s > best) { best = s; target = rushTarget; }
            }
        }

        if (Globals.paint >= 50) {
            for (MapInfo info : Globals.nearby) {
                if (!Navigation.isEnemyPaint(info.getPaint())) continue;
                int s = 4000 - Globals.myLoc.distanceSquaredTo(info.getMapLocation());
                if (s > best) { best = s; target = info.getMapLocation(); }
            }
        }

        if (Globals.paint >= 100 && rc.getHealth() > 50) {
            for (RobotInfo e : Globals.enemies) {
                if (!Globals.isTowerType(e.getType()) || Globals.isDefenseTower(e.getType())) continue;
                int s = 2000 - Globals.myLoc.distanceSquaredTo(e.getLocation());
                if (s > best) { best = s; target = e.getLocation(); }
            }
        }

        if (target != null) {
            boolean targetTower = false;
            for (RobotInfo e : Globals.enemies) {
                if (Globals.isTowerType(e.getType()) && e.getLocation().equals(target)) {
                    targetTower = true;
                    break;
                }
            }
            if (targetTower) {
                int dist = Globals.myLoc.distanceSquaredTo(target);
                if (dist > 13) Navigation.moveToward(target);
                else if (dist <= 9) Navigation.retreatFrom(target);
            } else {
                Navigation.pathTo(target);
            }
        } else {
            Navigation.moveExpansion();
        }
    }
}
