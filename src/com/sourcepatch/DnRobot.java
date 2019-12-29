package com.sourcepatch;

import java.awt.Color;

import robocode.AdvancedRobot;
import robocode.BulletHitBulletEvent;
import robocode.BulletHitEvent;
import robocode.BulletMissedEvent;
import robocode.CustomEvent;
import robocode.DeathEvent;
//import java.awt.Color;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.RobotDeathEvent;
import robocode.RobotStatus;
import robocode.RoundEndedEvent;
import robocode.Rules;
import robocode.ScannedRobotEvent;
import robocode.SkippedTurnEvent;
import robocode.StatusEvent;
import robocode.WinEvent;

// API help : https://robocode.sourceforge.io/docs/robocode/robocode/Robot.html

/**
 * SourcePatch - a robot by (your name here)
 */
public class DnRobot extends AdvancedRobot {
	private static final double THRESHOLD_ASSURED_FIRE = 25;
	int scanDirection = 1;
	boolean charge = false;
	boolean evade = false;
	boolean targetAcquired = false;
	double lastBearing = 0;
	int scanCycle = 0;
	int maxRobotsScanned = 0;
	int robotsScanned = 0;
	double evadeBearing = 0;
	double maxDistance = 0;
	private RobotStatus lastStatus = null;

	private static final long TARGET_TANK_LAPSE_TIME = 60;
	String targetTank = "";
	long targetTankLastSeenTime = 0;
	double targetTankDistance = 0;

	/**
	 * run: SourcePatch's default behavior
	 */
	public void run() {
		// Initialization of the robot should be put here
		maxDistance = Math
				.sqrt(getBattleFieldHeight() * getBattleFieldHeight() + getBattleFieldWidth() * getBattleFieldWidth());

		// Robot main loop
		while (true) {
//			setColors();

			if (!charge) {
				if (!evade) {
					maneuver(16);
					radarSweep();
				}
			}
			execute();
		}
	}

	/**
	 * onScannedRobot: What to do when you see another robot
	 */
	public void onScannedRobot(ScannedRobotEvent e) {
		if (e.getDistance() < 60) {
			System.out.println("Near range target of opportunity: " + e.getName() + ", distance: " + e.getDistance());
			acquireTargetTank(e.getName(), e.getDistance());
			setFire(Rules.MAX_BULLET_POWER);
			return;
		}
		if (e.getDistance() < 100 && e.getVelocity() < 1.0) {
			System.out.println("Short range target of opportunity: " + e.getName() + ", distance: " + e.getDistance());
			acquireTargetTank(e.getName(), e.getDistance());
			setFire(Rules.MAX_BULLET_POWER * (1 - e.getDistance() / maxDistance));
			return;
		}
		if (!targetTank.isEmpty() && !e.getName().equals(targetTank)) {
			return;
		}
		acquireTargetTank(e.getName(), e.getDistance());
		assuredFire(e);

		if (e.getDistance() < 40) {
			if (e.getEnergy() / getEnergy() < 1.3) {
				shortDistanceCharge(e);
			} else {
				evade(e);
			}
			return;
		}
		if (e.getDistance() < 120) {
			if (e.getEnergy() / getEnergy() < 1.3) {
				mediumDistanceCharge(e);
			} else {
				evade(e);
			}
			return;
		}

		mediumDistanceCharge(e);

		robotsScanned++;
		maxRobotsScanned = Math.max(maxRobotsScanned, robotsScanned);
//		execute();
		targetAcquired = false;
		charge = false;

		// }
	}

	private void assuredFire(ScannedRobotEvent e) {

		if (e.getDistance() < 40) {
			System.out.println("Point blank shot. Tank:" + ", name:" + e.getName() + ", distance:" + e.getDistance());
			setFire(Rules.MAX_BULLET_POWER);
//			scanDirection = -scanDirection;
		} else {

			double headingDifference = e.getHeading() - getGunHeading();
			double headingDelta = Math.abs(headingDifference);
			if (headingDelta > 175) {
				headingDelta = Math.abs(180 - headingDelta);
			}
			double headingAmplified = (headingDelta * Math.abs(e.getVelocity()) + e.getDistance() / 20);
			System.out.println("Considering shot, score: " + headingAmplified + ", gun heading delta:" + headingDelta
					+ ", name:" + e.getName() + ", distance:" + e.getDistance() + ", velocity:" + e.getVelocity()
					+ ", gunHeading:" + getGunHeading() + ", bearing:" + e.getBearing() + ", heading:"
					+ e.getHeading());
			if (headingAmplified <= THRESHOLD_ASSURED_FIRE) {
				double firePower = (headingAmplified > 0.1)
						? Rules.MAX_BULLET_POWER * (1 - headingAmplified / THRESHOLD_ASSURED_FIRE)
						: Rules.MAX_BULLET_POWER;
				System.out.println("Taking shot, score: " + headingAmplified + ", fire power: " + firePower);
				setFire(firePower);
//				scanDirection = -scanDirection;
			} else {
				System.out.println("Skipping shot, score: " + headingAmplified);
			}
		}
		setColors();
	}

	/**
	 * onHitByBullet: What to do when you're hit by a bullet
	 */
	public void onHitByBullet(HitByBulletEvent e) {
		System.out.println("Hit by: " + e.getName() + ", power:" + e.getBullet().getPower());
	}

	/**
	 * onHitWall: What to do when you hit a wall
	 */
	public void onHitWall(HitWallEvent e) {
		System.out.println("Hit wall. Heading:" + getHeading() + ", bearing:" + e.getBearing() + ", x:" + getX()
				+ ", y:" + getY());
		evade = false;
		charge = false;
	}

	@Override
	public void onCustomEvent(CustomEvent e) {
		super.onCustomEvent(e);
		System.out.println(e.getClass());
	}

	@Override
	public void onDeath(DeathEvent e) {
		super.onDeath(e);
		System.out.println("Death @" + e.getTime());
	}

	@Override
	public void onSkippedTurn(SkippedTurnEvent e) {
		super.onSkippedTurn(e);
		System.out.println(e.getSkippedTurn());
	}

	@Override
	public void onBulletHit(BulletHitEvent e) {
		super.onBulletHit(e);
		System.out.println("Bullet hit: " + e.getName() + " : " + e.getBullet().getPower());
	}

	@Override
	public void onBulletHitBullet(BulletHitBulletEvent e) {
		super.onBulletHitBullet(e);
		System.out.println("Bullet hit bullet: " + e.getHitBullet().getName() + "/" + e.getHitBullet().getName() + " : "
				+ e.getBullet().getPower());
	}

	@Override
	public void onBulletMissed(BulletMissedEvent e) {
		super.onBulletMissed(e);
		System.out.println("Bullet missed: " + e.getTime());
	}

	@Override
	public void onHitRobot(HitRobotEvent e) {
		super.onHitRobot(e);
		charge = true;
		if (e.isMyFault()) {
			System.out.println("Ram tank: " + e.getName() + ", energy:" + e.getEnergy() + ", bearing:" + e.getBearing()
					+ ", heading:" + getHeading() + ", gHeading:" + getGunHeading() + ", isAdjustedGun: "
					+ isAdjustGunForRobotTurn());
		} else {
			System.out.println("Rammed by tank: " + e.getName() + " : " + e.getEnergy() + " : " + e.getBearing());
		}
		setTurnRight(e.getBearing());
		double headingGunDifference = getHeading() - getGunHeading();
		if (Math.abs(headingGunDifference) < 180) {
			setTurnGunRight(headingGunDifference);
		} else {
			setTurnGunRight(180 - headingGunDifference);
		}
		execute();

		// Determine a shot that won't kill the robot...
		// We want to ram him instead for bonus points
		if (getEnergy() > e.getEnergy()) {
			double firePower = 0.0;
			if (e.getEnergy() > 16) {
				firePower =3;
			} else if (e.getEnergy() > 10) {
				firePower =2;
			} else if (e.getEnergy() > 4) {
				firePower =1;
			} else if (e.getEnergy() > 2) {
				firePower =0.5;
			} else if (e.getEnergy() > .4) {
				firePower =0.1;
			}
			if (firePower > 0 && firePower < e.getEnergy()) {
				System.out.println("Ram fire. Power:" + firePower);
				setFire(firePower);
			}
		}
		setAhead(40); // Ram him again!
		execute();
		charge=false;
	}

	@Override
	public void onRobotDeath(RobotDeathEvent e) {
		super.onRobotDeath(e);
		System.out.println("Robot killed: " + e.getName());
		if (e.getName().equals(targetTank)) {
			targetTank = "";
		}
	}

	@Override
	public void onRoundEnded(RoundEndedEvent e) {
		super.onRoundEnded(e);
		System.out.println("Round ended: " + e.getTurns());
	}

	@Override
	public void onStatus(StatusEvent e) {
		super.onStatus(e);
		lastStatus = e.getStatus();
	}

	@Override
	public void onWin(WinEvent e) {
		super.onWin(e);
	}

	private void radarSweep() {
		robotsScanned = 0;
		if (!targetAcquired && getEnergy() > 0) {
			System.out.println("Radar sweep: " + 10 * scanDirection);
			setTurnGunRight(10 * scanDirection);
		}
	}

	private void maneuver(double step) {
		System.out.println("Maneuvering. X,Y: " + getX() + "," + getY() + ", heading:" + getHeading() + ", velocity:"
				+ getVelocity() + ", distrem:" + getDistanceRemaining() + ", gHeading: " + getGunHeading()
				+ ", gTurnRem:" + getGunTurnRemaining());
		System.out.println("Target: " + targetTank);

		double safetyMargin = getDistanceRemaining() * 2;
		double tankRightMostEdge = getX() + getWidth() + safetyMargin;
		double tankYMostEdge = getY() + getHeight() + safetyMargin;

		boolean courseCorrection = false;

		if (tankRightMostEdge > getBattleFieldWidth() && getHeading() > 0 && getHeading() < 180) {
			setTargetHeadingAgainstXEdge();
			courseCorrection = true;
		}
		if (tankYMostEdge > getBattleFieldHeight() && (getHeading() > 270 || getHeading() < 90)) {
			setTargetHeadingAgainstYEdge();
			courseCorrection = true;
		}

		double tankXLeastEdge = getX() - getWidth() - safetyMargin;
		if (tankXLeastEdge < 0 && getHeading() > 180 && getHeading() < 360) {
			setTargetHeadingAgainstXEdge();
			courseCorrection = true;
		}
		double tankYLeastEdge = getY() - getHeight() - safetyMargin;
		if (tankYLeastEdge < 0 && (getHeading() > 90 && getHeading() < 270)) {
			setTargetHeadingAgainstYEdge();
			courseCorrection = true;
		}

		if (courseCorrection) {
			setAhead(0);
		} else {
			setAhead(step);
		}
	}

	private void setTargetHeadingAgainstYEdge() {
		if (getX() / getBattleFieldWidth() < 0.5) {
			setTargetHeading(90);
		} else {
			setTargetHeading(270);
		}
	}

	private void setTargetHeadingAgainstXEdge() {
		if (getY() / getBattleFieldHeight() < 0.5) {
			setTargetHeading(0);
		} else {
			setTargetHeading(180);
		}
	}

	private void setTargetHeading(double targetHeading) {
		if (Math.abs(getTurnRemaining()) > 0) {
			System.out.println("Skipping course correction towards heading: " + targetHeading + ", remaining turn:"
					+ Math.abs(getTurnRemaining()));
			return;
		}
		double h = getHeading();
		double targetHeadingDiff = targetHeading - h;
		if (Math.abs(targetHeadingDiff) > 180) {
			targetHeadingDiff = targetHeadingDiff - 360;
		}
		if (targetHeadingDiff < 0) {
			setTurnLeft(Math.abs(targetHeadingDiff));
		} else {
			setTurnRight(targetHeadingDiff);
		}

		String turnLabel = targetHeadingDiff > 0 ? "right" : "left";
		System.out.println("Set target heading to: " + targetHeading + ". Turning " + turnLabel + " "
				+ targetHeadingDiff + " degrees");
	}

	private void evade(ScannedRobotEvent e) {
		System.out.println("Evading: " + e.getName());
		if (e.getEnergy() > getEnergy()) {
			setTurnRight(e.getBearing() + 90);
			execute();
		}
		charge = false;
	}

	private void mediumDistanceCharge(ScannedRobotEvent e) {
		charge = true;
		System.out.println("Engage mid distance. Robot:" + e.getName() + ", distance:" + e.getDistance());
		setTurnRight(e.getBearing());
		setAhead(16);
		execute();
		charge = false;
	}

	private void shortDistanceCharge(ScannedRobotEvent e) {
		charge = true;
		System.out.println("Engage short distance. Robot:" + e.getName() + ", distance:" + e.getDistance());
		setTurnRight(e.getBearing());
		setAhead(e.getDistance());
		charge = false;
	}

	private boolean acquireTargetTank(String potentialTargetName, double tankDistance) {
		boolean result = false;

		if (((targetTankLastSeenTime + TARGET_TANK_LAPSE_TIME) < getTime()) || isTankBetterTarget(tankDistance)) {
			System.out.println("Giving up on target tank: " + targetTank + ", time:" + getTime());
			targetTank = "";
		}

		if (targetTank.isEmpty()) {
			targetTank = potentialTargetName;
			System.out.println("Target tank acquired:" + targetTank + ", time: " + getTime());
			scanDirection = -scanDirection;
			result = true;
		}

		if (targetTank.equals(potentialTargetName)) {
			targetTankDistance = tankDistance;
		}
		
		return result;
	}

	private boolean isTankBetterTarget(double tankDistance) {
		return targetTankDistance > 200 && (tankDistance <= targetTankDistance);
	}

	private void setColors() {
		if (getEnergy() < 40) {
			setBodyColor(Color.RED);
		} else {
			setBodyColor(Color.BLUE);
		}
		if (getGunHeat() > 1) {
			setGunColor(Color.RED);
		} else if (getGunHeat() > 0) {
			setGunColor(Color.ORANGE);
		} else {
			setGunColor(Color.BLUE);
		}
		if (targetAcquired) {
			setRadarColor(Color.RED);
		} else {
			setRadarColor(Color.BLUE);
		}
	}

}
