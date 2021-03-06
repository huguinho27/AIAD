package build;

import sajas.sim.repasts.RepastSLauncher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import agents.Explorer;
import entities.Entity;
import entities.Exit;
import entities.Obstacle;
import entities.UndiscoveredCell;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.StaleProxyException;
import repast.simphony.context.Context;
import repast.simphony.context.space.continuous.ContinuousSpaceFactory;
import repast.simphony.context.space.continuous.ContinuousSpaceFactoryFinder;
import repast.simphony.context.space.graph.NetworkBuilder;
import repast.simphony.context.space.grid.GridFactory;
import repast.simphony.context.space.grid.GridFactoryFinder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.parameter.Parameters;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.NdPoint;
import repast.simphony.space.continuous.RandomCartesianAdder;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridBuilderParameters;
import repast.simphony.space.grid.SimpleGridAdder;
import repast.simphony.space.grid.StrictBorders;
import sajas.wrapper.ContainerController;
import sajas.core.Runtime;
import utils.Coordinates;
import utils.ObjectSetups;

public class RepastSMapExplorationLauncher extends RepastSLauncher {

	private static int NUM_AGENTS = 1;
	private static int NUM_SUPER_AGENTS = 0;
	private static int COMMUNICATION_LIMIT = 10;
	private static int VISION_RADIOUS = 2;
	private static int MAX_GRID_X = 15;
	private static int MAX_GRID_Y = 15;
	private static int NUM_OBSTACLES = 0;

	private ContainerController mainContainer;
	private ArrayList<Explorer> explorers;

	@Override
	public String getName() {
		return "Map Exploration Test";
	}

	@Override
	protected void launchJADE() {
		Runtime runtime = Runtime.instance();
		Profile profile = new ProfileImpl();
		mainContainer = runtime.createMainContainer(profile);
		launchAgents();
	}

	private void launchAgents() {
		try {
			for (int i = 0; i < NUM_AGENTS + NUM_SUPER_AGENTS; i++)
				mainContainer.acceptNewAgent("Explorer_" + i, explorers.get(i)).start();
		} catch (StaleProxyException e) {
			e.printStackTrace();
		}
	}

	@Override
	public Context build(Context<Object> context) {
		// Get Parameters
		Parameters params = RunEnvironment.getInstance().getParameters();

		NUM_AGENTS = params.getInteger("numberOfAgents");
		NUM_SUPER_AGENTS = params.getInteger("numberSuperAgents");
		MAX_GRID_X = params.getInteger("gridSizeX");
		MAX_GRID_Y = params.getInteger("gridSizeY");
		VISION_RADIOUS = params.getInteger("visionRadius");
		COMMUNICATION_LIMIT = params.getInteger("communicationLimit");

		NetworkBuilder<Object> netBuilder = new NetworkBuilder<Object>("Map Exploration Network", context, true);
		netBuilder.buildNetwork();
		context.setId("Map Exploration");

		ContinuousSpaceFactory spaceFactory = ContinuousSpaceFactoryFinder.createContinuousSpaceFactory(null);
		ContinuousSpace<Object> space = spaceFactory.createContinuousSpace("space", context,
				new RandomCartesianAdder<Object>(), new repast.simphony.space.continuous.StrictBorders(), MAX_GRID_X,
				MAX_GRID_Y);

		GridFactory gridFactory = GridFactoryFinder.createGridFactory(null);
		Grid<Object> grid = gridFactory.createGrid("grid", context, new GridBuilderParameters<Object>(
				new StrictBorders(), new SimpleGridAdder<Object>(), true, MAX_GRID_X, MAX_GRID_Y));

		// Create instances of agents.
		explorers = new ArrayList<Explorer>();
		for (int i = 0; i < NUM_AGENTS; i++) {
			Explorer explorer = new Explorer(space, grid, VISION_RADIOUS, COMMUNICATION_LIMIT,
					NUM_AGENTS + NUM_SUPER_AGENTS, context);
			explorers.add(explorer);
			context.add(explorer);
		}

		for (int i = 0; i < NUM_SUPER_AGENTS; i++) {
			Explorer explorer = new Explorer(space, grid, VISION_RADIOUS, NUM_AGENTS + NUM_SUPER_AGENTS, context);
			explorers.add(explorer);
			context.add(explorer);
		}

		List<Coordinates> coordinates = new ArrayList<Coordinates>();
		for (int row = 0; row < MAX_GRID_Y; row++) {
			for (int column = 0; column < MAX_GRID_X; column++)
				coordinates.add(new Coordinates(column, row));
		}

		// Create obstacles.
		//for (int i = 0; i < NUM_OBSTACLES; i++)
		//	context.add(new Obstacle(5 + i, 6));
		ObjectSetups.Setup2(context, coordinates);
		// ObjectSetups.Setup3(context, coordinates);
		
		// Create the exit entity.
		int index = ThreadLocalRandom.current().nextInt(0, coordinates.size() - 1);
		//context.add(new Exit(5, 6));
		context.add(new Exit(coordinates.get(index).getX(), coordinates.get(index).getY()));
		coordinates.remove(index);

		for (int i = 0; i < coordinates.size(); i++)
			context.add(new UndiscoveredCell(coordinates.get(i).getX(), coordinates.get(i).getY()));

		// Updates/Sets all the objects location.
		for (Object obj : context) {
			if (obj instanceof Explorer) {
				index = ThreadLocalRandom.current().nextInt(0, coordinates.size() - 1);
				space.moveTo(obj, coordinates.get(index).getX(), coordinates.get(index).getY());
				grid.moveTo(obj, coordinates.get(index).getX(), coordinates.get(index).getY());
				coordinates.remove(index);
			} else if (obj instanceof Entity) {
				space.moveTo(obj, ((Entity) obj).getCoordinates().getX(), ((Entity) obj).getCoordinates().getY());
				grid.moveTo(obj, ((Entity) obj).getCoordinates().getX(), ((Entity) obj).getCoordinates().getY());
			} else {
				NdPoint pt = space.getLocation(obj);
				grid.moveTo(obj, (int) pt.getX(), (int) pt.getY());
			}
		}

		return super.build(context);
	}
}