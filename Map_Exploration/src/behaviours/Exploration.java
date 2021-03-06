package behaviours;

import java.util.Iterator;
import java.util.List;

import agents.Explorer;
import algorithms.astar.AStar;
import algorithms.dfs.DFS;
import algorithms.pledge.Pledge;
import communication.GroupMessage;
import communication.IndividualMessage;
import entities.Exit;
import entities.UndiscoveredCell;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;
import repast.simphony.query.space.grid.GridCell;
import repast.simphony.query.space.grid.GridCellNgh;
import repast.simphony.space.grid.GridPoint;
import sajas.core.behaviours.CyclicBehaviour;
import states.Explore;
import states.IAgentState;
import states.IAgentTemporaryState;
import states.ObstacleGuardian;
import states.Recruiting;
import states.TravelNearestUndiscovered;
import states.WaitingForObstacleDestroy;
import utils.Coordinates;
import utils.Matrix;
import utils.Utils;
import utils.Utils.AgentType;
import utils.Utils.MessageType;

public class Exploration extends CyclicBehaviour {

	private static final long serialVersionUID = 7526472295622776147L; // unique
																		// id

	private Explorer agent;
	private DFS dfs;
	private AStar astar;
	private Pledge pledge;
	private IAgentState currState;
	private IAgentState pausedState;
	private boolean newPausedState;

	public Exploration(Explorer agent) {
		super(agent);
		this.agent = agent;
		dfs = new DFS(agent);
		astar = new AStar(agent);
		pledge = new Pledge(agent);
		changeState(new Explore());
		newPausedState = false;
	}

	@Override
	public void action() {
		updateDynamicEnvironment();

		if (currState != null) {
			if (currState instanceof IAgentTemporaryState && ((IAgentTemporaryState) currState).canResume())
				this.resumeState();
			else
				currState.execute();
		}
		if (agent != null) {
			receiveMessagesHandler();
			List<GridCell<Object>> neighborhoodCells = getNeighborhoodCellsCommunicationLimit();
			if (neighborhoodCells != null)
				sendMessagesHandler(neighborhoodCells);
		}

		resetDynamicNotWalkable();
	}

	private void updateDynamicEnvironment() {
		for (int row = 0; row < agent.getGrid().getDimensions().getHeight(); row++) {
			for (int column = 0; column < agent.getGrid().getDimensions().getWidth(); column++) {
				Iterator<Object> it = agent.getGrid().getObjectsAt(column, row).iterator();
				boolean hasExit = false;
				boolean hasExplorer = false;
				boolean isObstacleGuardian = false;
				while (it.hasNext()) {
					Object obj = it.next();
					if (obj instanceof Explorer) {
						hasExplorer = true;
						if (((Explorer) obj).getState() instanceof ObstacleGuardian
								|| ((Explorer) obj).getState() instanceof WaitingForObstacleDestroy)
							isObstacleGuardian = true;
					} else if (obj instanceof Exit)
						hasExit = true;
				}
				if (hasExplorer && !hasExit && !isObstacleGuardian)
					astar.addDynamicNotWalkable(new Coordinates(column, row));
			}
		}
	}

	private void resetDynamicNotWalkable() {
		astar.resetDynamicNotWalkable();
	}

	private void receiveMessagesHandler() {
		ACLMessage acl;
		while ((acl = agent.receiveMessage()) != null) {
			if (acl.getSender().getLocalName().equals(agent.getLocalName()))
				continue;
			try {
				Object obj = acl.getContentObject();
				if (obj instanceof IndividualMessage) {
					IndividualMessage message = (IndividualMessage) obj;
					switch (message.getMessageType()) {
					case MATRIX:
						Matrix otherMatrix = (Matrix) message.getContent();
						agent.getMatrix().mergeMatrix(otherMatrix, this);
						break;
					case HELP:
						if (!(currState instanceof WaitingForObstacleDestroy)) {
							System.err.println("HELP MESSAGE HERE");
							this.changeState(new WaitingForObstacleDestroy());
						}
						break;
					case OBSTACLEDOOR_DESTROYED:
						Coordinates obstacleCoordinates = (Coordinates) message.getContent();
						agent.getMatrix().updateMatrix(this, agent.getGrid(), obstacleCoordinates, agent.getRadious());
						astar.setNodeWalkable(obstacleCoordinates, true);
						pledge.addVisitedCoordinates(obstacleCoordinates);
						this.changeState(new TravelNearestUndiscovered());
						break;
					case OTHER_GUARDING:
						boolean isToExit = (boolean) message.getContent();
						if (isToExit)
							agent.exitFromSimulation();
						else {
							if (!(currState instanceof Recruiting))
								changeState(new Recruiting());
						}
						break;
					default:
						break;
					}
				} else if (obj instanceof GroupMessage) {
					if (agent.getAgentType() == AgentType.SUPER_AGENT)
						agent.getSendingMessagesBehaviour().checkReceiver(acl.getSender());

					GroupMessage message = (GroupMessage) obj;
					switch (message.getMessageType()) {
					case MATRIX:
						Matrix otherMatrix = (Matrix) message.getContent();
						agent.getMatrix().mergeMatrix(otherMatrix, this);
						break;
					default:
						break;
					}

				}
			} catch (UnreadableException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Searches for other explorers in the neighborhood and sends them
	 * WAITING_TO_BREAK
	 * 
	 * @param neighborhoodCells
	 */
	private void sendMessagesHandlerBreakObstacle(List<GridCell<Explorer>> neighborhoodCells) {
		for (GridCell<Explorer> gridCell : neighborhoodCells) {
			Iterator<Explorer> it = gridCell.items().iterator();
			while (it.hasNext()) {
				Object obj = it.next();
				if (obj instanceof Explorer) {
					Explorer otherExplorer = (Explorer) obj;
					sendMessageToNeighbor(otherExplorer, Utils.MessageType.WAITING_TO_BREAK);
				}
			}
		}
	}

	private void sendMessageToNeighbor(Explorer otherExplorer, MessageType msgType) {
		// Super agents don't need to exchange matrix messages when they're
		// close to each other.
		if (agent.getAgentType() == AgentType.SUPER_AGENT && otherExplorer.getAgentType() == AgentType.SUPER_AGENT)
			return;

		IndividualMessage message = new IndividualMessage(msgType, null, otherExplorer.getAID());
		agent.sendMessage(message);
	}

	public void changeState(IAgentState newState) {
		if (newState instanceof IAgentTemporaryState) { // If it's a temporary
														// state
			if (pausedState != null) {
				// Will change the current state and leave the paused alone
				currState.exit();
			} else {
				// Pauses the sate and uses the new one instead
				pauseState();
			}
			currState = newState;
			currState.enter(this);
		} else { // If it's not a temporary state
			if (pausedState != null) {
				// Switches the paused state for the new one
				pausedState.exit();
				pausedState = newState;
				newPausedState = true;
			} else {
				// Switches the current state with the new one
				if (currState != null)
					currState.exit();
				currState = newState;
				currState.enter(this);
			}
		}
	}

	public GridPoint getAgentPoint() {
		return agent.getGrid().getLocation(agent);
	}

	public Coordinates getAgentCoordinates() {
		GridPoint pt = getAgentPoint();
		if (pt == null)
			return null;
		return new Coordinates(pt.getX(), pt.getY());
	}

	public List<GridCell<Object>> getNeighborhoodCells() {
		GridPoint pt = getAgentPoint();
		// This can happen when the agent has already left and the behaviour is
		// finishing executing.
		if (pt == null)
			return null;
		GridCellNgh<Object> nghCreator = new GridCellNgh<Object>(agent.getGrid(), pt, Object.class, agent.getRadious(),
				agent.getRadious());
		return nghCreator.getNeighborhood(false);
	}

	public List<GridCell<Object>> getNeighborhoodCellsCommunicationLimit() {
		GridPoint pt = getAgentPoint();
		// This can happen when the agent has already left and the behaviour is
		// finishing executing.
		if (pt == null)
			return null;
		GridCellNgh<Object> nghCreator = new GridCellNgh<Object>(agent.getGrid(), pt, Object.class,
				agent.getCommLimit(), agent.getCommLimit());
		return nghCreator.getNeighborhood(false);
	}

	public List<GridCell<Explorer>> getNeighborhoodCellsWithExplorers() {
		GridPoint pt = getAgentPoint();
		GridCellNgh<Explorer> nghCreator = new GridCellNgh<Explorer>(agent.getGrid(), pt, Explorer.class,
				agent.getRadious(), agent.getRadious());
		return nghCreator.getNeighborhood(false);
	}

	public List<GridCell<Explorer>> getNeighborhoodCellsWithExplorersCommunicationLimit() {
		GridPoint pt = getAgentPoint();
		GridCellNgh<Explorer> nghCreator = new GridCellNgh<Explorer>(agent.getGrid(), pt, Explorer.class,
				agent.getCommLimit(), agent.getCommLimit());
		return nghCreator.getNeighborhood(true);
	}

	public boolean moveAgentToCoordinate(Coordinates targetCoordinates) {
		return agent.moveAgent(targetCoordinates);
		// TODO: it's possible 2 agents stop moving if they want to go to each
		// other's place.
	}

	public void discoverCell(UndiscoveredCell cell) {
		agent.discoverCell(cell);
	}

	public void printStates() {
		System.out.println("Agent: " + agent.getName() + "@" + getAgentCoordinates() + "; State: " + currState
				+ "; Paused State: " + pausedState);
	}

	/**
	 * Searches for other explorers in the neighborhood and sends them his
	 * matrix.
	 * 
	 * @param neighborhoodCells
	 */
	private void sendMessagesHandler(List<GridCell<Object>> neighborhoodCells) {
		for (GridCell<Object> gridCell : neighborhoodCells) {
			Iterator<Object> it = gridCell.items().iterator();
			while (it.hasNext()) {
				Object obj = it.next();
				if (obj instanceof Explorer) {
					Explorer otherExplorer = (Explorer) obj;
					sendMessageToNeighbor(otherExplorer);
				}
			}
		}
	}

	private void sendMessageToNeighbor(Explorer otherExplorer) {
		// Super agents don't need to exchange matrix messages when they're
		// close to each other.
		if (agent.getAgentType() == AgentType.SUPER_AGENT && otherExplorer.getAgentType() == AgentType.SUPER_AGENT)
			return;

		IndividualMessage message = new IndividualMessage(MessageType.MATRIX, agent.getMatrix(),
				otherExplorer.getAID());
		agent.sendMessage(message);
	}

	private void pauseState() {
		this.pausedState = this.currState;
		this.currState = null;
	}

	private void resumeState() {
		this.currState = this.pausedState;
		this.pausedState = null;
		if (this.newPausedState)
			this.currState.enter(this);
		this.newPausedState = false;
	}

	public DFS getDFS() {
		return dfs;
	}

	public AStar getAStar() {
		return astar;
	}

	public Explorer getAgent() {
		return agent;
	}

	public Pledge getPledge() {
		return pledge;
	}

	public IAgentState getState() {
		return currState;
	}
}
