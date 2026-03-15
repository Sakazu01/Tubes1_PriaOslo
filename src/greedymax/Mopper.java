package greedymax;

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
        if (Globals.paint > 40) {
            for (RobotInfo ally : Globals.allies) {
                if (ally.getType() != UnitType.SOLDIER) continue;
                if (ally.getPaintAmount() >= 30) continue;
                MapLocation allyLoc = ally.getLocation();
                if (Globals.myLoc.distanceSquaredTo(allyLoc) <= 2
                        && rc.canTransferPaint(allyLoc, 25)) {
                    rc.transferPaint(allyLoc, 25);
                    return;
                }
            }
        }

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

        MapLocation bersihRuin = null;
        int bd = Integer.MAX_VALUE;
        for (MapInfo info : Globals.nearby) {
            if (!Navigation.isEnemyPaint(info.getPaint())) continue;
            MapLocation loc = info.getMapLocation();
            if (Globals.myLoc.distanceSquaredTo(loc) > 2) continue;
            for (int i = 0; i < Globals.numRuins; i++) {
                if (loc.distanceSquaredTo(Globals.ruins[i]) <= 12) {
                    int d = Globals.myLoc.distanceSquaredTo(loc);
                    if (d < bd) { bd = d; bersihRuin = loc; }
                    break;
                }
            }
        }
        if (bersihRuin != null && rc.canAttack(bersihRuin)) { rc.attack(bersihRuin); return; }

        MapLocation bersih = null;
        bd = Integer.MAX_VALUE;
        for (MapInfo info : Globals.nearby) {
            if (!Navigation.isEnemyPaint(info.getPaint())) continue;
            MapLocation loc = info.getMapLocation();
            if (Globals.myLoc.distanceSquaredTo(loc) > 2) continue;
            int d = Globals.myLoc.distanceSquaredTo(loc);
            if (d < bd) { bd = d; bersih = loc; }
        }
        if (bersih != null && rc.canAttack(bersih)) rc.attack(bersih);
    }

    static void gerak() throws GameActionException {
        RobotController rc = Globals.rc;
        if (!rc.isMovementReady()) return;

        MapLocation target = null;
        int best = -1;

        int urgency = Math.max(0, (30 - Globals.paint) * 30);
        if (urgency > best) {
            MapLocation tower = Globals.nearestPaintTower(Globals.myLoc);
            if (tower == null) tower = Globals.nearestAllyTower(Globals.myLoc);
            if (tower != null) {
                int s = urgency - Globals.myLoc.distanceSquaredTo(tower);
                if (s > best) { best = s; target = tower; }
            }
        }

        // deketin soldier yang mau kehabisan paint
        if (Globals.paint > 40) {
            for (RobotInfo ally : Globals.allies) {
                if (ally.getType() != UnitType.SOLDIER) continue;
                if (ally.getPaintAmount() >= 30) continue;
                int s = 4000 - Globals.myLoc.distanceSquaredTo(ally.getLocation());
                if (s > best) { best = s; target = ally.getLocation(); }
            }
        }

        for (RobotInfo e : Globals.enemies) {
            if (Globals.isTowerType(e.getType())) continue;
            int s = 3000 - Globals.myLoc.distanceSquaredTo(e.getLocation());
            if (s > best) { best = s; target = e.getLocation(); }
        }

        if (best < 2000) {
            outer:
            for (MapInfo info : Globals.nearby) {
                if (!Navigation.isEnemyPaint(info.getPaint())) continue;
                MapLocation loc = info.getMapLocation();
                for (int i = 0; i < Globals.numRuins; i++) {
                    if (loc.distanceSquaredTo(Globals.ruins[i]) <= 12) {
                        int s = 2000 - Globals.myLoc.distanceSquaredTo(loc);
                        if (s > best) { best = s; target = loc; }
                        continue outer;
                    }
                }
            }
        }

        if (best < 1000) {
            for (MapInfo info : Globals.nearby) {
                if (!Navigation.isEnemyPaint(info.getPaint())) continue;
                int s = 1000 - Globals.myLoc.distanceSquaredTo(info.getMapLocation());
                if (s > best) { best = s; target = info.getMapLocation(); }
            }
        }

        if (target == null) {
            // kalo ga ada kerjaan, ikutin soldier terdekat
            RobotInfo nearestSoldier = null;
            int nd = Integer.MAX_VALUE;
            for (RobotInfo ally : Globals.allies) {
                if (ally.getType() != UnitType.SOLDIER) continue;
                int d = Globals.myLoc.distanceSquaredTo(ally.getLocation());
                if (d < nd) { nd = d; nearestSoldier = ally; }
            }
            if (nearestSoldier != null) {
                target = nearestSoldier.getLocation();
            } else {
                if (exploreTarget == null || Globals.myLoc.distanceSquaredTo(exploreTarget) <= 4) {
                    idleCount++;
                    if (idleCount > 15) {
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
                target = exploreTarget;
            }
        }

        gerakHindariMusuh(target);
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
