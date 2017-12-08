package agents;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.sun.corba.se.impl.io.IIOPInputStream;

import algorithms.astar.AStar;
import algorithms.dfs.DFS;
import algorithms.pledge.Pledge;
import behaviours.Exploration;
import behaviours.ReceivingMessages;
import behaviours.SendingMessages;
import communication.GroupMessage;
import communication.IndividualMessage;
import entities.DiscoveredCell;
import entities.Entity;
import entities.Exit;
import entities.Obstacle;
import entities.UndiscoveredCell;
import jade.core.AID;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import repast.simphony.context.Context;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridPoint;
import sajas.core.Agent;
import sajas.domain.DFService;
import utils.Coordinates;
import utils.Matrix;
import utils.Utils.AgentType;

public class Explorer extends Agent {
	
	private ContinuousSpace<Object> space;
	private Grid<Object> grid;
	private int radious;
	private int communicationLimit;
	private int totalNumAgents;
	private AgentType agentType;
	private Context<Object> context;
	private Matrix matrix;
	
	private Exploration exploration;
	private SendingMessages sendingMessages;
			
	/**
	 * Super agent constructor.
	 * @param space
	 * @param grid
	 * @param radious
	 */
	public Explorer(ContinuousSpace<Object> space, Grid<Object> grid, int radious, int totalNumAgents, Context<Object> context) {
		this.space = space;
		this.grid = grid;
		this.radious = radious;
		this.totalNumAgents = totalNumAgents;
		this.context = context;
		agentType = AgentType.SUPER_AGENT;
	}
	
	/**
	 * Normal agent constructor.
	 * @param space
	 * @param grid
	 * @param radious
	 * @param communicationLimit
	 */
	public Explorer(ContinuousSpace<Object> space, Grid<Object> grid, int radious, int communicationLimit, int totalNumAgents, Context<Object> context) {
		this.space = space;
		this.grid = grid;
		this.radious = radious;
		this.communicationLimit = communicationLimit;
		this.totalNumAgents = totalNumAgents;
		this.context = context;
		agentType = AgentType.NORMAL_AGENT;
	}
	
	@Override
	public void setup() {
		DFAgentDescription dfAgentDescription = new DFAgentDescription();
		dfAgentDescription.setName(getAID());
		ServiceDescription serviceDescription = new ServiceDescription();
		serviceDescription.setName(getName());
		if(agentType == AgentType.NORMAL_AGENT)
			serviceDescription.setType("Normal Explorer");
		else serviceDescription.setType("Super Explorer");
		dfAgentDescription.addServices(serviceDescription);
		
		try {
			DFService.register(this, dfAgentDescription);
		} catch(FIPAException e) {
			e.printStackTrace();
		}

		matrix = new Matrix(grid.getDimensions().getHeight(), grid.getDimensions().getWidth(), getName());
		
		// Sets his initial position in the matrix.
		GridPoint initLocation = grid.getLocation(this);
		matrix.setValue(initLocation.getX(), grid.getDimensions().getHeight() - 1 - initLocation.getY(), 1);		
		
		exploration = new Exploration(this);
		addBehaviour(exploration);
		// addBehaviour(new ReceivingMessages(this));
		
		if(agentType == AgentType.SUPER_AGENT) {
			DFAgentDescription template = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("Super Explorer");
			template.addServices(sd);
			try {
				DFAgentDescription[] results = DFService.search(this, template);
				List<AID> resultsFiltered = new ArrayList<>();
				
				// Take himself out from of the results.
				for(int i = 0; i < results.length; i++) {
					if(results[i].getName() != getAID())
						resultsFiltered.add(results[i].getName());
				}
				
				sendingMessages = new SendingMessages(this, resultsFiltered);
				addBehaviour(sendingMessages);
			} catch (FIPAException e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public void takeDown() {
		try {
			DFService.deregister(this);
		} catch(FIPAException e) {
			e.printStackTrace();
		}
	}
	
	public boolean moveAgent(Coordinates targetCoordinates) {
		if(canMove(targetCoordinates)) {
			if (space.moveTo(this, targetCoordinates.getX(), targetCoordinates.getY())) {
				grid.moveTo(this, targetCoordinates.getX(), targetCoordinates.getY());
				getMatrix().updateMatrix(exploration, getGrid(), targetCoordinates, getRadious());
				return true;
			}
		}
		return false;
	}
	
	/**
	 * If the target coordinates has an obstacle, it returns false. Otherwise, it returns true;
	 * @param targetCoordinates
	 * @return Returns if the agent can move to the specified coordinates.
	 */
	private boolean canMove(Coordinates targetCoordinates) {
		Iterator<Object> it = grid.getObjectsAt(targetCoordinates.getX(), targetCoordinates.getY()).iterator();
		while(it.hasNext()) {
			Object obj = it.next();
			if(obj instanceof Obstacle)//  || obj instanceof Explorer)
				return false;
		}
		return true;
	}
	
	public void discoverCell(UndiscoveredCell cell) {
		System.out.println("Discovered => x: " + cell.getCoordinates().getX() + ", y: " + cell.getCoordinates().getY());
		DiscoveredCell discoveredCell = new DiscoveredCell(cell.getCoordinates().getX(), cell.getCoordinates().getY());
		context.add(discoveredCell);
		space.moveTo(discoveredCell, discoveredCell.getCoordinates().getX(), discoveredCell.getCoordinates().getY());
		grid.moveTo(discoveredCell, discoveredCell.getCoordinates().getX(), discoveredCell.getCoordinates().getY());
		context.remove(cell);
	}
	
	public void exitFromSimulation() {
		takeDown();
		context.remove(this);
	}
	
	/*******************************/
	/*********** Messages **********/
	/*******************************/
	
	public void sendMessage(IndividualMessage message) {
		ACLMessage acl = new ACLMessage(ACLMessage.INFORM);
		acl.addReceiver(message.getReceiver());
		try {
			acl.setContentObject(message);
		} catch (IOException e) {
			e.printStackTrace();
		}
		send(acl);
	}
	
	public void sendMessage(GroupMessage message) {
		ACLMessage acl = new ACLMessage(ACLMessage.INFORM);
		for(int i = 0; i < message.getReceivers().size(); i++)
			acl.addReceiver(message.getReceivers().get(i));
		try {
			acl.setContentObject((Serializable) message);
		} catch (IOException e) {
			e.printStackTrace();
		}
		send(acl);
	}
	
	public ACLMessage receiveMessage() {
		return receive();
	}
	
	/*******************************/
	/***** Getters and setters *****/
	/*******************************/
	
	public Grid<Object> getGrid() {
		return grid;
	}
	
	public int getRadious() {
		return radious;
	}
	
	public ContinuousSpace<Object> getSpace() {
		return space;
	}
	
	public Matrix getMatrix() {
		return matrix;
	}
	
	public SendingMessages getSendingMessagesBehaviour() {
		return sendingMessages;
	}
	
	public AgentType getAgentType() {
		return agentType;
	}
	
	public int getTotalNumAgents() {
		return totalNumAgents;
	}
}
