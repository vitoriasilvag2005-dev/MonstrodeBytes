package github;
import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.awt.geom.Point2D;

// API help : https://robocode.sourceforge.io/docs/robocode/robocode/Robot.html

/**
 * PrimeiroRobo - a robot by (JGuilherme)
 * Push para teste de webhook / alterado package
 */
public class PrimeiroRobo extends AdvancedRobot
{
	// Estado do inimigo
	private double lastEnemyEnergy = 100.0;
	private int moveDirection = 1; // 1 ou -1
	private long lastDirectionChange = 0;
	private static final double WALL_MARGIN = 40;

	/**
	 * run: PrimeiroRobo's default behavior
	 */
	public void run() {
		setColors(Color.green, Color.green, Color.blue); // body, gun, radar
		setAdjustGunForRobotTurn(true); // Gun independente do corpo
		setAdjustRadarForGunTurn(true); // Radar independente da arma
		// Loop principal: varrer o radar continuamente
		while(true) {
			// Mantém radar girando para achar / manter lock
			setTurnRadarRight(360);
			execute();
		}
	}

	/**
	 * onScannedRobot: What to do when you see another robot
	 */
	public void onScannedRobot(ScannedRobotEvent e) {
		// Bearing absoluto em graus
		double absBearing = getHeading() + e.getBearing();
		// Travar radar: sobrecompensa para manter lock
		double radarTurn = Utils.normalRelativeAngleDegrees(absBearing - getRadarHeading());
		setTurnRadarRight(radarTurn * 2);

		// Escolha de potência adaptativa
		double distance = e.getDistance();
		double firePower;
		if (distance > 600) firePower = 1.0;
		else if (distance > 350) firePower = 1.8;
		else if (distance > 180) firePower = 2.2;
		else firePower = 3.0;
		if (getEnergy() < 20) firePower = Math.min(firePower, 1.5); // Conserva energia

		// Coordenadas atuais do inimigo (estimativa inicial)
		double enemyX = getX() + distance * Math.sin(Math.toRadians(absBearing));
		double enemyY = getY() + distance * Math.cos(Math.toRadians(absBearing));

		// Linear prediction
		double enemyHeading = e.getHeading();
		double enemyVelocity = e.getVelocity();
		double bulletSpeed = 20 - 3 * firePower;
		double time = 0.0;
		double predictedX = enemyX;
		double predictedY = enemyY;
		while ((++time) * bulletSpeed < Point2D.distance(getX(), getY(), predictedX, predictedY) && time < 60) {
			predictedX += enemyVelocity * Math.sin(Math.toRadians(enemyHeading));
			predictedY += enemyVelocity * Math.cos(Math.toRadians(enemyHeading));
			// Limita dentro da arena
			predictedX = Math.max(18, Math.min(getBattleFieldWidth() - 18, predictedX));
			predictedY = Math.max(18, Math.min(getBattleFieldHeight() - 18, predictedY));
		}

		// Apontar arma
		double theta = Math.toDegrees(Math.atan2(predictedX - getX(), predictedY - getY()));
		double gunTurn = Utils.normalRelativeAngleDegrees(theta - getGunHeading());
		setTurnGunRight(gunTurn);

		// Movimento perpendicular + evasão por perda de energia (deteção de tiro)
		double energyDrop = lastEnemyEnergy - e.getEnergy();
		if (energyDrop > 0 && energyDrop <= 3.0) { // Provável tiro do inimigo
			moveDirection = -moveDirection;
			lastDirectionChange = getTime();
		}

		// Altera direção periodicamente para imprevisibilidade
		if (getTime() - lastDirectionChange > 40) {
			moveDirection = -moveDirection;
			lastDirectionChange = getTime();
		}

		// Calcular movimento perpendicular suavizado para paredes
		double goAngle = Math.toRadians(absBearing + 90 * moveDirection);
		goAngle = wallSmoothing(goAngle, moveDirection);
		setTurnRightRadians(Utils.normalRelativeAngle(goAngle - Math.toRadians(getHeading())));
		setAhead(150 * moveDirection);

		// Atira se a arma estiver alinhada razoavelmente
		if (Math.abs(gunTurn) < 20 && getGunHeat() == 0) {
			setFire(firePower);
		}

		lastEnemyEnergy = e.getEnergy();
	}

	// Smoothing simples: ajusta ângulo para não colidir com paredes
	private java.awt.geom.Point2D.Double getPoint(double x, double y){
		return new java.awt.geom.Point2D.Double(x,y);
	}

	private double wallSmoothing(double angle, int orientation) {
		// Trabalha em radianos
		for (int i = 0; i < 10; i++) {
			double testX = getX() + 120 * Math.sin(angle);
			double testY = getY() + 120 * Math.cos(angle);
			if (testX > WALL_MARGIN && testX < getBattleFieldWidth() - WALL_MARGIN && testY > WALL_MARGIN && testY < getBattleFieldHeight() - WALL_MARGIN) {
				break; // seguro
			}
			angle += orientation * Math.toRadians(10); // ajusta ângulo
		}
		return angle;
	}

	/**
	 * onHitByBullet: What to do when you're hit by a bullet
	 */
	public void onHitByBullet(HitByBulletEvent e) {
		// Evasão rápida: muda direção e desloca
		moveDirection = -moveDirection;
		setAhead(120 * moveDirection);
		setTurnRight(90 - e.getBearing());
	}
	
	/**
	 * onHitWall: What to do when you hit a wall
	 */
	public void onHitWall(HitWallEvent e) {
		moveDirection = -moveDirection;
		setAhead(150 * moveDirection);
		setTurnRight(100);
	}	
}
