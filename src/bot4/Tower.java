package bot4;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;


public class Tower extends Entity {
	private final UnitType[] spawnCycle = {
		UnitType.SOLDIER,
		UnitType.SOLDIER,
		UnitType.SOLDIER,
		UnitType.SOLDIER,
		UnitType.SPLASHER,
		UnitType.SPLASHER,
        UnitType.SPLASHER,
		UnitType.MOPPER
	};
	private int spawnIndex = 0;

	public Tower(RobotController rc) {
		super(rc);
	}

	@Override
	public void run() throws GameActionException {
		scan();
		attackEnemies();
		spawnByRatio();
		count++;
	}

	private void attackEnemies() throws GameActionException {
		RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
		if (enemies.length == 0) return;

		RobotInfo target = enemies[0];
		for (RobotInfo enemy : enemies)
			if (enemy.health < target.health) target = enemy;

		if (rc.canAttack(target.location)) {
			rc.attack(target.location);
		}
	}

	private void spawnByRatio() throws GameActionException {
		UnitType toSpawn = spawnCycle[spawnIndex % spawnCycle.length];
		for (var d : directions) {
			MapLocation spawnLoc = rc.getLocation().add(d);
			if (rc.canBuildRobot(toSpawn, spawnLoc)) {
				rc.buildRobot(toSpawn, spawnLoc);
				spawnIndex++;
				return;
			}
		}
	}
}
