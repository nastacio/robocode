package com.sourcepatch;

import java.awt.Color;
import java.util.Map;
import java.util.TreeMap;

import robocode.AdvancedRobot;
import robocode.BulletHitBulletEvent;
import robocode.BulletHitEvent;
import robocode.BulletMissedEvent;
import robocode.CustomEvent;
import robocode.DeathEvent;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.RobotDeathEvent;
import robocode.RoundEndedEvent;
import robocode.Rules;
import robocode.ScannedRobotEvent;
import robocode.SkippedTurnEvent;
import robocode.StatusEvent;
import robocode.WinEvent;

// API help : https://robocode.sourceforge.io/docs/robocode/robocode/Robot.html

/**
 * SourcePatch - a robot by Denilson Nastacio
 */
public class DnRobot extends AdvancedRobot {
	private static double pointBlankRange;
	private static final double THRESHOLD_ASSURED_FIRE = 25;
	private int scanDirection = 1;
	private boolean charge = false;
	private boolean evade = false;
	private boolean fullSweepInNextCycle = false;
	private Map<String, ScannedRobotEvent> robotsScanned = new TreeMap<>();
	private static double maxDistance;

	private static final long TARGET_TANK_LAPSE_TIME = 60;
	private String targetTank = "";
	private long targetTankLastSeenTime = 0;
	private double targetTankDistance = 0;

	/**
	 * run: SourcePatch's default behavior
	 */
	public void run() {
		maxDistance = Math
				.sqrt(getBattleFieldHeight() * getBattleFieldHeight() + getBattleFieldWidth() * getBattleFieldWidth());
		pointBlankRange = Math.min(getWidth(), getHeight()) * 2;

		while (true) {
			if (!charge) {
				if (!evade) {
					maneuver(16);
					gunSweep(10);
				}
			}
			execute();
		}
	}

	/**
	 * onScannedRobot: What to do when you see another robot
	 */
	public void onScannedRobot(ScannedRobotEvent e) {
		robotsScanned.put(e.getName(), e);

		if (e.getName().equals(targetTank)) {
			lockOnTarget(e);
		}

		assuredFire(e);
		acquireTargetTank(e);

		if (e.getDistance() < pointBlankRange) {
			if (e.getEnergy() / getEnergy() < 1.3) {
				shortDistanceCharge(e);
			} else {
				evadeTank(e);
			}
			return;
		}
		if (e.getEnergy() / getEnergy() < 1.3) {
			mediumDistanceCharge(e);
		} else {
			evadeTank(e);
		}

		charge = false;
	}

	private void lockOnTarget(ScannedRobotEvent e) {

		double gunTargetDiff = bearingDiff(bearingDiff(getGunHeading(), getHeading()), e.getBearing());
		scanDirection = gunTargetDiff > 0 ? -1 : +1;

		System.out.println("Lock on target. Tank:" + e.getName() + ", gun-target bearing:" + gunTargetDiff
				+ ", scan dir:" + scanDirection + ", bearing:" + e.getBearing() + ", heading:" + getHeading()
				+ ", turn remaining:" + getTurnRemaining() + ", gun heading:" + getGunHeading() + ", gun turn rem:"
				+ getGunTurnRemaining());
	}

	private void assuredFire(ScannedRobotEvent e) {

		if (e.getDistance() < pointBlankRange) {
			System.out.println("Point blank shot. Tank:" + ", name:" + e.getName() + ", distance:" + e.getDistance());
			setFire(Rules.MAX_BULLET_POWER);
		} else {
			double headingDifference = e.getHeading() - getGunHeading();
			double headingDelta = Math.abs(headingDifference);
			if (headingDelta > 175) {
				headingDelta = Math.abs(180 - headingDelta);
			}
			double headingAmplified = (headingDelta * Math.abs(e.getVelocity()) + e.getDistance() / 20);
			double firePower = (headingAmplified > 0.1)
					? Rules.MAX_BULLET_POWER * (1 - headingAmplified / THRESHOLD_ASSURED_FIRE)
					: Rules.MAX_BULLET_POWER;
			System.out.println("Considering shot, score: " + headingAmplified + ", power: " + firePower
					+ ", gun heading delta:" + headingDelta + ", name:" + e.getName() + ", distance:" + e.getDistance()
					+ ", velocity:" + e.getVelocity() + ", gunHeading:" + getGunHeading() + ", bearing:"
					+ e.getBearing() + ", heading:" + e.getHeading());
			if (headingAmplified <= THRESHOLD_ASSURED_FIRE) {
				setFire(firePower);
			} else {
//				fullSweepInNextCycle = true;
			}
		}
		setColors();
	}

	/**
	 * onHitByBullet:
	 */
	public void onHitByBullet(HitByBulletEvent e) {
		System.out.println(
				"Hit by: " + e.getName() + ", power:" + e.getBullet().getPower() + ", bearing:" + e.getBearing());
		if (isHitByHeadOnShot(e) || isHitByNarrowAngleRearShot(e)) {
			System.out.println("Evading head on fire. Tank: " + e.getName() + ", bearing:" + e.getBearing() + ", power:"
					+ e.getPower() + ", velocity:" + e.getVelocity());
			setTurnRight(45);
			charge = false;
			execute();
		}
	}

	private boolean isHitByHeadOnShot(HitByBulletEvent e) {
		return getEnergy() < 50 && Math.abs(e.getBearing()) > 170;
	}

	private boolean isHitByNarrowAngleRearShot(HitByBulletEvent e) {
		return Math.abs(e.getBearing()) < 20 && e.getPower() >= 1.0;
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
		if (targetTank.equals(e.getName())) {
			targetTankLastSeenTime = getTime();
		}
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
			System.out.println("Ram tank: " + e.getName() + ", target energy:" + e.getEnergy() + ", bearing:"
					+ e.getBearing() + ", energy:" + getEnergy() + ", heading:" + getHeading() + ", gHeading:"
					+ getGunHeading() + ", isAdjustedGun: " + isAdjustGunForRobotTurn());
			if (targetTank.equals(e.getName())) {
				targetTankLastSeenTime = getTime();
			}
		} else {
			System.out.println("Rammed by tank: " + e.getName() + " : " + e.getEnergy() + " : " + e.getBearing());
		}
		double headingGunDifference = getHeading() - getGunHeading();
		if (Math.abs(headingGunDifference) <= 180) {
			setTurnGunRight(headingGunDifference);
		} else if (headingGunDifference < -180) {
			setTurnGunLeft(360 + headingGunDifference);
		} else {
			setTurnGunLeft(360 - headingGunDifference);
		}
		if (getEnergy() / e.getEnergy() > 2) {
			setTurnRight(e.getBearing());
		} else {
			setTurnRight(e.getBearing() + 90);
		}
		execute();

		// Determine a shot that won't kill the robot...
		// We want to ram him instead for bonus points
		double firePower = 0.0;
		if (e.getEnergy() > 16 || getEnergy() < e.getEnergy()) {
			firePower = 3;
		} else if (e.getEnergy() > 10) {
			firePower = 2;
		} else if (e.getEnergy() > 4) {
			firePower = 1;
		} else if (e.getEnergy() > 2) {
			firePower = 0.5;
		} else if (e.getEnergy() > .4) {
			firePower = 0.1;
		}
		if (firePower > 0 && firePower < e.getEnergy()) {
			System.out.println("Ram fire. Power:" + firePower + ", gun heat:" + getGunHeat());
			setFire(firePower);
		}
		setAhead(40); // Ram him again!
		execute();
		charge = false;
	}

	@Override
	public void onRobotDeath(RobotDeathEvent e) {
		super.onRobotDeath(e);
		System.out.println("Robot killed: " + e.getName());

		robotsScanned.remove(e.getName());
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
	}

	@Override
	public void onWin(WinEvent e) {
		super.onWin(e);
	}

	private void gunSweep(double step) {

		if (getEnergy() > 0) {
			double angle = fullSweepInNextCycle ? 360 * scanDirection : step * scanDirection;
			System.out.println("Gun sweep. Step:" + angle + ", turn remaining:" + getGunTurnRemaining());
			setTurnGunRight(angle);
			fullSweepInNextCycle = false;
		}
	}

	private void maneuver(double step) {
		System.out.println("Maneuvering. X,Y: " + getX() + "," + getY() + ", heading:" + getHeading() + ", velocity:"
				+ getVelocity() + ", distrem:" + getDistanceRemaining() + ", turn remaining:" + getTurnRemaining()
				+ ", scanDirection:" + scanDirection + ", gHeading: " + getGunHeading() + ", gTurnRem:"
				+ getGunTurnRemaining());
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

	private void evadeTank(ScannedRobotEvent e) {
		System.out.println("Evading: " + e.getName());
		if (e.getEnergy() > getEnergy()) {
			setTurnRight(e.getBearing() + 90);
			setAhead(16);
			scanDirection = -1;
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

	private boolean acquireTargetTank(ScannedRobotEvent e) {
		boolean result = false;

		if (targetTank.equals(e.getName())) {
			targetTankDistance = e.getDistance();
		} else {
			if ((targetTankLastSeenTime + TARGET_TANK_LAPSE_TIME) < getTime()) {
				System.out.println("Giving up on target tank (" + targetTank + ") due time. Time:" + getTime());
				targetTank = "";
			} else if (isTankBetterTarget(e.getDistance())) {
				System.out.println("Giving up on target tank (" + targetTank + ") for nearby tank. Tank: " + e.getName()
						+ ", time:" + getTime());
				targetTank = "";
			}

			if (targetTank.isEmpty()) {
				targetTank = e.getName();
				System.out.println("Target tank acquired:" + targetTank + ", time: " + getTime() + ", gun heading"
						+ getGunHeading() + ", target bearing:" + e.getBearing() + ", scan direction: "
						+ scanDirection);
				result = true;
			}
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
		if (targetTank.isEmpty()) {
			setRadarColor(Color.BLUE);
		} else {
			setRadarColor(Color.GREEN);
		}
	}

	private double bearingDiff(double dend, double dstart) {
		double result = dend - dstart;
		if (result > 180) {
			result = 360 - result;
		} else if (result < -180) {
			result = 360 + result;
		}

		return result;
	}

}
