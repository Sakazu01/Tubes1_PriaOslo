package alternative_bots_1;

import battlecode.common.*;

public class Mopper {

    static MapLocation exploreTarget = null;
    static int idleCount = 0;

    static void run() throws GameActionException {
        if (Globals.rc.isActionReady()) aksi();
        gerak();
        if (Globals.rc.isActionReady()) aksi();
    }

    static void aksi() throws GameActionException {
        RobotController rc = Globals.rc;
        if (!rc.isActionReady()) return;

        // kasih paint ke soldier yang butuh
        for (RobotInfo ally : Globals.allies) {
            if (ally.getType() != UnitType.SOLDIER) continue;
            if (ally.getPaintAmount() > 20) continue;
            MapLocation allyLoc = ally.getLocation();
            if (rc.canTransferPaint(allyLoc, 25)) {
                rc.transferPaint(allyLoc, 25);
                return;
            }
        }

        // swing ke arah yang kena paling banyak musuh
        Direction[] cardinal = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
        Direction swingDir = null;
        int swingCount = 0;
        for (Direction dir : cardinal) {
            if (!rc.canMopSwing(dir)) continue;
            MapLocation s1 = Globals.myLoc.add(dir);
            MapLocation s2 = s1.add(dir);
            int cnt = 0;
            for (RobotInfo e : Globals.enemies) {
                if (Globals.isTowerType(e.getType())) continue;
                MapLocation el = e.getLocation();
                if (el.distanceSquaredTo(s1) <= 2 || el.distanceSquaredTo(s2) <= 2) cnt++;
            }
            if (cnt > swingCount) { swingCount = cnt; swingDir = dir; }
        }
        if (swingDir != null) { rc.mopSwing(swingDir); return; }

        // serang musuh terdekat dalam jangkauan
        RobotInfo targetMop = null;
        int lowestHP = Integer.MAX_VALUE;
        for (RobotInfo e : Globals.enemies) {
            if (Globals.isTowerType(e.getType())) continue;
            if (Globals.myLoc.distanceSquaredTo(e.getLocation()) > 2) continue;
            if (e.getHealth() < lowestHP) { lowestHP = e.getHealth(); targetMop = e; }
        }
        if (targetMop != null && rc.canAttack(targetMop.getLocation())) {
            rc.attack(targetMop.getLocation());
            return;
        }

        // bersihkan cat musuh dekat ruin
        for (MapInfo info : Globals.nearby) {
            if (!Navigation.isEnemyPaint(info.getPaint())) continue;
            MapLocation loc = info.getMapLocation();
            if (Globals.myLoc.distanceSquaredTo(loc) > 2) continue;
            for (int i = 0; i < Globals.numRuins; i++) {
                if (loc.distanceSquaredTo(Globals.ruins[i]) <= 12) {
                    if (rc.canAttack(loc)) { rc.attack(loc); return; }
                    break;
                }
            }
        }

        // bersihkan cat musuh umum
        for (MapInfo info : Globals.nearby) {
            if (!Navigation.isEnemyPaint(info.getPaint())) continue;
            MapLocation loc = info.getMapLocation();
            if (Globals.myLoc.distanceSquaredTo(loc) > 2) continue;
            if (rc.canAttack(loc)) { rc.attack(loc); return; }
        }
    }

    static void gerak() throws GameActionException {
        RobotController rc = Globals.rc;
        if (!rc.isMovementReady()) return;

        // refuel
        if (Globals.paint < 20) {
            MapLocation tower = Globals.nearestPaintTower(Globals.myLoc);
            if (tower == null) tower = Globals.nearestAllyTower(Globals.myLoc);
            if (tower != null) { gerakHindariMusuh(tower); return; }
        }

        // kejar musuh non-tower
        for (RobotInfo e : Globals.enemies) {
            if (Globals.isTowerType(e.getType())) continue;
            gerakHindariMusuh(e.getLocation());
            return;
        }

        // bersihkan cat musuh dekat ruin
        for (MapInfo info : Globals.nearby) {
            if (!Navigation.isEnemyPaint(info.getPaint())) continue;
            MapLocation loc = info.getMapLocation();
            for (int i = 0; i < Globals.numRuins; i++) {
                if (loc.distanceSquaredTo(Globals.ruins[i]) <= 12) {
                    gerakHindariMusuh(loc);
                    return;
                }
            }
        }

        // ikutin soldier terdekat
        for (RobotInfo ally : Globals.allies) {
            if (ally.getType() != UnitType.SOLDIER) continue;
            gerakHindariMusuh(ally.getLocation());
            return;
        }

        // explore
        if (exploreTarget == null || Globals.myLoc.distanceSquaredTo(exploreTarget) <= 4) {
            idleCount++;
            if (idleCount > 10) {
                Globals.symType = (Globals.symType + 1) % 3;
                idleCount = 0;
            }
            MapLocation mt = Globals.firstMoneyTower();
            if (mt != null)
                exploreTarget = Globals.predictEnemy(mt);
            else
                exploreTarget = new MapLocation(
                    Globals.rng.nextInt(Globals.mapW),
                    Globals.rng.nextInt(Globals.mapH));
        }
        gerakHindariMusuh(exploreTarget);
    }

    static void gerakHindariMusuh(MapLocation target) throws GameActionException {
        RobotController rc = Globals.rc;
        if (target == null || !rc.isMovementReady()) return;

        Direction toTarget = Globals.myLoc.directionTo(target);
        Direction[] coba = {
            toTarget, toTarget.rotateLeft(), toTarget.rotateRight(),
            toTarget.rotateLeft().rotateLeft(), toTarget.rotateRight().rotateRight(),
            toTarget.opposite().rotateLeft(), toTarget.opposite().rotateRight()
        };

        Direction pilihan = null;
        int bestSkor = Integer.MIN_VALUE;
        for (Direction d : coba) {
            if (!rc.canMove(d)) continue;
            MapLocation dest = Globals.myLoc.add(d);

            boolean aman = true;
            for (RobotInfo e : Globals.enemies) {
                if (Globals.isTowerType(e.getType()) && dest.distanceSquaredTo(e.getLocation()) <= 9) {
                    aman = false;
                    break;
                }
            }
            if (!aman) continue;

            int skor = -dest.distanceSquaredTo(target) * 10;
            if (rc.canSenseLocation(dest)) {
                PaintType pt = rc.senseMapInfo(dest).getPaint();
                if (Navigation.isAllyPaint(pt)) skor += 5;
                else if (pt == PaintType.EMPTY) skor += 1;
                else if (Navigation.isEnemyPaint(pt)) skor -= 20;
            }
            if (skor > bestSkor) { bestSkor = skor; pilihan = d; }
        }

        if (pilihan != null) rc.move(pilihan);
        else Navigation.moveRandom();
    }
}
