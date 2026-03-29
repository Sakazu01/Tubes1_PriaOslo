package alternative_bots_2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

/**
 * Shared mobile-unit base for bot4 troop classes.
 */
public abstract class Robot extends Entity {

	public Robot(RobotController rc) {
		super(rc);
	}

	@Override
	public void run() throws GameActionException {
		scan();
		runUnitTurn();

		count++;
	}

	protected abstract void runUnitTurn() throws GameActionException;

	protected MapLocation findNearestRuinTarget() throws GameActionException {
		MapLocation myLoc = rc.getLocation();
		MapLocation best = null;
		int bestDist = Integer.MAX_VALUE;

		MapLocation[] sensed = rc.senseNearbyRuins(-1);
		for (MapLocation r : sensed) {
			if (rc.canSenseLocation(r) && rc.canSenseRobotAtLocation(r)) continue;
			int d = myLoc.distanceSquaredTo(r);
			if (d < bestDist) {
				bestDist = d;
				best = r;
			}
		}

		if (best != null) return best;

		for (MapLocation r : ruins) {
			int d = myLoc.distanceSquaredTo(r);
			if (d < bestDist) {
				bestDist = d;
				best = r;
			}
		}

		return best;
	}

	protected boolean isEdgeTile(MapLocation loc, MapInfo[] nearby) {
		for (MapInfo tile : nearby) {
			MapLocation n = tile.getMapLocation();
			if (!loc.isWithinDistanceSquared(n, 2) || loc.equals(n)) continue;
			if (!tile.getPaint().isAlly()) return true;
		}
		return false;
	}

	protected void paintUnderfoot() throws GameActionException {
		MapLocation here = rc.getLocation();
		MapInfo tile = rc.senseMapInfo(here);
		if (!tile.getPaint().isAlly() && rc.canAttack(here)) {
			rc.attack(here, false);
		}
	}

	protected boolean moveTowardRuinWorkTile(MapLocation ruin) throws GameActionException {
		MapLocation myLoc = rc.getLocation();
		MapLocation best = null;
		int bestDist = Integer.MAX_VALUE;
		for (Direction d : directions) {
			MapLocation cand = ruin.add(d);
			int dist = myLoc.distanceSquaredTo(cand);
			if (dist < bestDist) {
				bestDist = dist;
				best = cand;
			}
		}
		if (best != null) return moveToward(best);
		return moveToward(ruin);
	}

	protected boolean moveToward(MapLocation target) throws GameActionException {
		if (target == null || rc.getLocation().equals(target)) return true;
		Direction dir = rc.getLocation().directionTo(target);
		for (int i = 0; i < 4; i++) {
			if (rc.canMove(dir)) {
				rc.move(dir);
				return true;
			}
			if (rc.canMove(dir.rotateRight())) {
				rc.move(dir.rotateRight());
				return true;
			}
			if (rc.canMove(dir.rotateLeft())) {
				rc.move(dir.rotateLeft());
				return true;
			}
			dir = dir.rotateLeft().rotateLeft();
		}
		return false;
	}

	protected boolean moveRandomly() throws GameActionException {
		int start = (rc.getRoundNum() + rc.getID()) % directions.length;
		for (int i = 0; i < directions.length; i++) {
			Direction d = directions[(start + i) % directions.length];
			if (rc.canMove(d)) {
				rc.move(d);
				return true;
			}
		}
		return false;
	}
}
