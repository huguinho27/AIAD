package agents;

import java.util.ArrayList;
import java.util.List;

import org.omg.CORBA.INTERNAL;

import algorithms.astar.Pathfinding;
import entities.Exit;
import entities.Obstacle;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;
import repast.simphony.query.space.grid.GridCell;
import repast.simphony.query.space.grid.GridCellNgh;
import repast.simphony.space.SpatialMath;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.NdPoint;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridPoint;
import repast.simphony.util.SimUtilities;
import sajas.core.AID;
import sajas.core.Agent;
import sajas.core.behaviours.CyclicBehaviour;
import sajas.domain.DFService;
import utils.Coordinates;
import utils.Utils.ExplorerState;

public class Explorer extends Agent {
	
	private ContinuousSpace<Object> space;
	private Grid<Object> grid;
	private int radious;
	private int communicationLimit;
	
	private int[][] matrix;
	private int iteration = 0;

	public Explorer(ContinuousSpace<Object> space, Grid<Object> grid, int radious, int communicationLimit) {
		this.space = space;
		this.grid = grid;
		this.radious = radious;
		this.communicationLimit = communicationLimit;
		
		matrix = new int[grid.getDimensions().getHeight()][grid.getDimensions().getWidth()];
		for(int row = 0; row < grid.getDimensions().getHeight(); row++) {
			for(int column = 0; column < grid.getDimensions().getWidth(); column++)
				matrix[row][column] = 0;
		}
	}
		
	@Override
	public void setup() {
		DFAgentDescription dfAgentDescription = new DFAgentDescription();
		dfAgentDescription.setName(getAID());
		
		ServiceDescription serviceDescription = new ServiceDescription();
		serviceDescription.setName(getName());
		serviceDescription.setType("Explorer");
		
		dfAgentDescription.addServices(serviceDescription);
		
		try {
			DFService.register(this, dfAgentDescription);
		} catch(FIPAException e) {
			e.printStackTrace();
		}

		// Sets his initial position in the matrix.
		GridPoint initLocation = grid.getLocation(this);
		System.out.println("Initial location -> x: " + initLocation.getX() + ", y: " + initLocation.getY());
		matrix[grid.getDimensions().getHeight() - 1 - initLocation.getY()][initLocation.getX()] = 1;
		System.out.println("y: " + (grid.getDimensions().getHeight() - 1 - initLocation.getY()) + ", x: " + initLocation.getX());
		
		addBehaviour(new AleatoryDFS(this));
	}
	
	public int[][] getMatrix() {
		return matrix;
	}

	/**
	 * @brief Merges the matrix of the receiving agent with the matrix received
	 *        from other agents inside the communication radius
	 * @param receivedMatrix
	 */
	public void mergeMatrix(int[][] receivedMatrix) {
		for (int i = 0; i < receivedMatrix.length; i++)
		{
			for (int j = 0; j < receivedMatrix[i].length; j++)
			{
				if (receivedMatrix[i][j] != 0 && matrix[i][j] == 0)
					matrix[i][j] = receivedMatrix[i][j];
			}
		}
	}
	
	private void printMatrix(NdPoint pt) {		
		System.out.println("Iteration " + iteration + "\nCurrent position -> x: " + (int)Math.round(pt.getX()) + ", y: " + (int)Math.round(pt.getY()));
		for(int row = 0; row < matrix.length; row++) {
			for(int column = 0; column < matrix[row].length; column++) {
				System.out.print(matrix[row][column] + " | ");
			}
			System.out.println(" " + (grid.getDimensions().getHeight() - 1 - row));
		}
		
		for(int column = 0; column < matrix[0].length; column++)
			System.out.print(column + "   ");
		
		System.out.println("\n");
	}
	
	class VerticalMovementBehaviour extends CyclicBehaviour {
		
		private Agent agent;
		
		public VerticalMovementBehaviour(Agent agent) {
			super(agent);
			this.agent = agent;
		}

		@Override
		public void action() {
			GridPoint pt = grid.getLocation(agent);
			NdPoint origin = space.getLocation(agent);
			space.moveByDisplacement(agent, 0, 1);
			origin = space.getLocation(agent);
			grid.moveTo(agent, (int) origin.getX(), (int) origin.getY());
			System.out.println("x: " + (int) Math.round(origin.getX()) + ", y: " + (int) Math.round(origin.getY()));
		}
	}

	class Pledge extends CyclicBehaviour {

		private Agent agent;
		private GridPoint startingPoint;
		private GridPoint previousPoint;

		Pledge (Agent agent) {
			super(agent);
			this.agent = agent;
			this.startingPoint = grid.getLocation(agent);
			this.previousPoint = this.startingPoint;
		}

		@Override
		public void action() {
			GridPoint pt = grid.getLocation(agent);
			if (this.previousPoint != this.startingPoint && pt == this.startingPoint) {
				System.out.println("SWITCH OUT OF PLEDGE!");
			}

			ArrayList<GridCell<Object>> possibleCells = new ArrayList<>();

			GridCellNgh<Object> nghCreator = new GridCellNgh<Object>(grid, pt, Object.class, 1, 1);
			GridPoint frontPt = pt, backPt = pt, rightPt = pt, leftPt = pt;

			if (pt.getX() > this.previousPoint.getX()) {
				// Facing Right
				frontPt = new GridPoint(pt.getX() + 1, pt.getY());
				backPt = new GridPoint(pt.getX() - 1, pt.getY());
				rightPt = new GridPoint(pt.getX(), pt.getY() - 1);
				leftPt = new GridPoint(pt.getX(), pt.getY() + 1);
			} else if (pt.getX() < this.previousPoint.getX()) {
				// Facing Left
				frontPt = new GridPoint(pt.getX() - 1, pt.getY());
				backPt = new GridPoint(pt.getX() + 1, pt.getY());
				rightPt = new GridPoint(pt.getX(), pt.getY() + 1);
				leftPt = new GridPoint(pt.getX(), pt.getY() - 1);
			} else if (pt.getY() > this.previousPoint.getY()) {
				// Facing Upwards
				frontPt = new GridPoint(pt.getX(), pt.getY() + 1);
				backPt = new GridPoint(pt.getX(), pt.getY() - 1);
				rightPt = new GridPoint(pt.getX() + 1, pt.getY());
				leftPt = new GridPoint(pt.getX() - 1, pt.getY());
			} else if (pt.getY() < this.previousPoint.getY()) {
				// Facing Downwards
				frontPt = new GridPoint(pt.getX(), pt.getY() - 1);
				backPt = new GridPoint(pt.getX(), pt.getY() + 1);
				rightPt = new GridPoint(pt.getX() - 1, pt.getY());
				leftPt = new GridPoint(pt.getX() + 1, pt.getY());
			}

			Object frontObject = grid.getObjectsAt(frontPt.getX(), frontPt.getY());
			//Object backObject = grid.getObjectsAt(backPt.getX(), backPt.getY());
			Object rightObject = grid.getObjectsAt(rightPt.getX(), rightPt.getY());
			//Object leftObject = grid.getObjectsAt(leftPt.getX(), leftPt.getY());

			if (rightObject == null) {
				moveAgent(rightPt);
			} else if (frontObject == null && rightObject instanceof Obstacle) {
				moveAgent(frontPt);
			} else if (frontObject instanceof Obstacle && rightObject instanceof Obstacle) {
				moveAgent(leftPt);
			} else moveAgent(backPt);

			this.previousPoint = pt;
		}

		private void moveAgent(GridPoint targetPoint) {
			NdPoint origin = space.getLocation(agent);
			NdPoint target = new NdPoint(targetPoint.getX(), targetPoint.getY());
			double angle = SpatialMath.calcAngleFor2DMovement(space, origin, target);
			space.moveByVector(agent, 1, angle, 0);
			origin = space.getLocation(agent);
			grid.moveTo(agent, (int) origin.getX(), (int) origin.getY());

			//matrix[grid.getDimensions().getHeight() - 1 - (int) origin.getY()][(int) origin.getX()] = 1;

			iteration++;
		}
	}
	
	class AleatoryDFS extends CyclicBehaviour {
		
		private Agent agent;
		private ExplorerState state;
		
		// A* vars.
		private Pathfinding pathfinding;
		private List<algorithms.astar.Node> path; 
		private int pathNode = 0;
		
		public AleatoryDFS(Agent agent) {
			super(agent);
			this.agent = agent;
			state = ExplorerState.ALEATORY_DFS;
			pathfinding = new Pathfinding(grid.getDimensions().getWidth(), grid.getDimensions().getHeight());
		}

		@Override
		public void action() {
			GridPoint pt = grid.getLocation(agent);
			
			switch(state) {
				case ALEATORY_DFS:
					GridCellNgh<Object> nghCreator = new GridCellNgh<Object>(grid, pt, Object.class, radious, radious);
					List<GridCell<Object>> gridCells = nghCreator.getNeighborhood(false);
					
					// SimUtilities.shuffle(gridCells, RandomHelper.getUniform());
					GridCell<Object> cell = null;			
					for(int i = 0; i < gridCells.size(); i++) {
						int row = grid.getDimensions().getHeight() - 1 - gridCells.get(i).getPoint().getY();
						int column = gridCells.get(i).getPoint().getX();
						
						// If the point is inside the grid...
						if(row >= 0 && row < grid.getDimensions().getWidth() && column >= 0 && column < grid.getDimensions().getHeight()) {
							if(matrix[row][column] != 1) {
								matrix[row][column] = 1;
								cell = gridCells.get(i);
							}	
						}
					}
					
					NdPoint origin = space.getLocation(agent);
					printMatrix(origin);
					iteration++;
					
					if(cell != null) {
						System.out.println("Moving to point => x: " + cell.getPoint().getX() + ", y: " + cell.getPoint().getY());
						moveAgent(cell.getPoint());
						break;
					} else {
						Coordinates nearestUndiscovered = getNearestUndiscoveredPlace(pt);
						if(nearestUndiscovered != null) {
							System.out.println("Nearest zero: x: " + nearestUndiscovered.getX() + ", y: " + nearestUndiscovered.getY());
							path = pathfinding.FindPath(new Coordinates(pt.getX(), pt.getY()), nearestUndiscovered);
							state = ExplorerState.A_STAR;
							/*for(int i = 0; i < path.size(); i++) {
								System.out.println((i+1) + " => x: " + path.get(i).getWorldPosition().getX() + ", y: " + path.get(i).getWorldPosition().getY());
							}*/
							//System.out.println("Changing to A*, going from\nx: " + pt.getX() + ", y: " + pt.getY() + " to\nx: " + path.get(path.size()-1).getWorldPosition().getX() + ", y: " + path.get(path.size()-1).getWorldPosition().getY() + "\n");
						} else {
							// The map is fully discovered, should we go for the exit?
							System.out.println("Map is fully discovered");
							state = ExplorerState.EXIT;
							break;
						}
					}
				case A_STAR:
					moveAgent(new Coordinates(path.get(pathNode).getWorldPosition().getX(), path.get(pathNode).getWorldPosition().getY()));
					pathNode++;
					
					if(pathNode == path.size()) {
						state = ExplorerState.ALEATORY_DFS;
						path = null;
						pathNode = 0;
					}
					break;
				case PLEDGE:
					break;
				case EXIT:
					break;
				default: break;
			}
		}
		
		private void moveAgent(GridPoint targetPoint) {
			NdPoint origin = space.getLocation(agent);
			//origin = new NdPoint((int)Math.round(origin.getX()), (int)Math.round(origin.getY()));
			NdPoint target = new NdPoint(targetPoint.getX(), targetPoint.getY());
			double angle = SpatialMath.calcAngleFor2DMovement(space, origin, target);
			//System.out.println("origin => x:" + origin.getX() + ", y: " + origin.getY() + ", target => x: " + target.getX() + ", y: " + target.getY());
			//System.out.println("Angulo: " + (angle*180/Math.PI));
			double distance = (((angle*180/Math.PI) % 5) == 0) ? utils.Utils.sqrt2 : 1;
			try {
				origin = space.moveByVector(agent, distance, angle, 0);
			} catch (repast.simphony.space.SpatialException e) {
				e.printStackTrace();
			}
			grid.moveTo(agent, (int)Math.round(origin.getX()), (int)Math.round(origin.getY()));
		}
		
		private void moveAgent(Coordinates targetCoordinates) {			
			NdPoint origin = space.getLocation(agent);
			//origin = new NdPoint((int)Math.round(origin.getX()), (int)Math.round(origin.getY()));
			NdPoint target = new NdPoint(targetCoordinates.getX(), targetCoordinates.getY());
			double angle = SpatialMath.calcAngleFor2DMovement(space, origin, target);
			//System.out.println("A* ====== origin => x:" + origin.getX() + ", y: " + origin.getY() + ", target => x: " + target.getX() + ", y: " + target.getY());
			//System.out.println("Angulo: " + (angle*180/Math.PI));
			double distance = (((angle*180/Math.PI) % 5) == 0) ? utils.Utils.sqrt2 : 1;
			try {
				origin = space.moveByVector(agent, distance, angle, 0);
			} catch (repast.simphony.space.SpatialException e) {
				e.printStackTrace();
			}
			grid.moveTo(agent, (int)Math.round(origin.getX()), (int)Math.round(origin.getY()));
		}
		
		/**
		 * Returns the nearest coordinate that has not yet been discovered based on the agent's position.
		 * @param currentPosition
		 * @return
		 */
		private Coordinates getNearestUndiscoveredPlace(GridPoint currentPosition) {
			Coordinates currCoordinates = matrixFromWorldPoint(currentPosition);
			Coordinates nearestUndiscovered = null;
			
			int maxDistance;	// Distance from the current position to the furthest edge of the matrix.
			if(grid.getDimensions().getWidth() > grid.getDimensions().getHeight()) {
				int matrixMaxIndexX = grid.getDimensions().getWidth() - 1;
				int leftCellsAmount = matrixMaxIndexX - currCoordinates.getX();
				int rightCellsAmount = matrixMaxIndexX - (matrixMaxIndexX - currCoordinates.getX());
				maxDistance = (leftCellsAmount > rightCellsAmount) ? leftCellsAmount : rightCellsAmount;
			} else {
				int matrixMaxIndexY = grid.getDimensions().getHeight() - 1;
				int leftCellsAmount = matrixMaxIndexY - currCoordinates.getY();
				int rightCellsAmount = matrixMaxIndexY - (matrixMaxIndexY - currCoordinates.getY());
				maxDistance = (leftCellsAmount > rightCellsAmount) ? leftCellsAmount : rightCellsAmount;
			}
						
			for(int radious = 2; radious < maxDistance; radious++) {
				nearestUndiscovered = getUndiscoveredInRadious(currCoordinates, radious);
				if(nearestUndiscovered != null)
					break;
			}
			
			return worldPointFromMatrix(nearestUndiscovered);
		}
		
		/**
		 * Returns the first coordinate that has a zero on the matrix (is undiscovered) distancing 'radious' from the agent.
		 * @param currCoordinates
		 * @param radious
		 * @return
		 */
		private Coordinates getUndiscoveredInRadious(Coordinates currCoordinates, int radious) {
			Coordinates nearestCoordinate = null;
			float nearestDistance = Float.MAX_VALUE;
			
			for(int column = currCoordinates.getX() - radious; column <= currCoordinates.getX() + radious; column++) {
				for(int row = currCoordinates.getY() - radious; row <= currCoordinates.getY() + radious; row++) {
					// Are the points on the grid?
					if(column >= 0 && column < grid.getDimensions().getWidth() && row >= 0 && row < grid.getDimensions().getHeight()) {
						// Do the points not belong in the radious?
						if(column != currCoordinates.getX() - radious && column != currCoordinates.getX() + radious && row != currCoordinates.getY() - radious && row != currCoordinates.getY() + radious)
							continue;
						
						Coordinates coordinates = new Coordinates(column, row);
						float distance = utils.Utils.getDistance(currCoordinates, coordinates);
						if(matrix[row][column] == 0 && distance < nearestDistance) {
							nearestCoordinate = coordinates;
							nearestDistance = distance;
						}
					}
				}
			}
			
			return nearestCoordinate;
		}
	}
	
	/**
	 * Calculates the matrix coordinate from a world point.
	 * @param point
	 * @return
	 */
	private Coordinates matrixFromWorldPoint(GridPoint point) {
		return new Coordinates(point.getX(), grid.getDimensions().getHeight() - 1 - point.getY());
	}
	
	private Coordinates worldPointFromMatrix(Coordinates coordinates) {
		return new Coordinates(coordinates.getX(), grid.getDimensions().getHeight() - 1 - coordinates.getY());
	}
	
	@Override
	public void takeDown() {
		try {
			DFService.deregister(this);
		} catch(FIPAException e) {
			e.printStackTrace();
		}
	}
}
