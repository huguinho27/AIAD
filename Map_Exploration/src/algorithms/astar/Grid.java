package algorithms.astar;

import java.util.ArrayList;
import java.util.List;

import utils.Coordinates;

public class Grid {

	private Node[][] grid;
	private int gridSizeX;
	private int gridSizeY;
	
	public Grid(int gridSizeX, int gridSizeY) {
		grid = new Node[gridSizeY][gridSizeX];
		this.gridSizeX = gridSizeX;
		this.gridSizeY = gridSizeY;
		
		for(int row = 0; row < gridSizeY; row++) {
			for(int column = 0; column < gridSizeX; column++)
				grid[row][column] = new Node(true, new Coordinates(column, row));
		}
	}
	
	/**
	 * Returns all the node's neighbours.
	 * @param node to get neighbours from.
	 * @return list of neighbours.
	 */
	public List<Node> getNeighbours(Node node) {
		List<Node> neighbours = new ArrayList<Node>();
		
		for(int x = -1; x <= 1; x++) {
			for(int y = -1; y <= 1; y++) {
				if(x == 0 && y == 0)
					continue;
				
				int checkX = node.getWorldPosition().getX() + x;
				int checkY = node.getWorldPosition().getY() + y;
				if(checkX >= 0 && checkX < gridSizeX && checkY >= 0 && checkY < gridSizeY)
					neighbours.add(grid[checkY][checkX]);
			}
		}
		
		return neighbours;
	}
	
	public Node getNode(Coordinates coordinates) {
		return grid[coordinates.getY()][coordinates.getX()];
	}
	
	/**
	 * Returns the node that is in the specified world position.
	 * @param worldPosition
	 * @return node that is in the world position.
	 */
	public Node nodeFromWorldPoint(Coordinates worldPosition) {
		return grid[gridSizeY - 1 - worldPosition.getY()][worldPosition.getX()];
	}
	
	public void setNodeWalkable(Coordinates coordinates, boolean newWalkable) {
		//nodeFromWorldPoint(coordinates).setWalkable(newWalkable);
		//printGrid();
		grid[coordinates.getY()][coordinates.getX()].setWalkable(newWalkable);
	}
	
	public void printGrid() {
		for(int row = 0; row < gridSizeY; row++) {
			for(int column = 0; column < gridSizeX; column++)
				System.out.print((grid[row][column].getWalkable() ? "1" : "0") + " | ");
			System.out.println(" " + (gridSizeY - 1 - row));
		}
		
		for(int column = 0; column < gridSizeX; column++)
			System.out.print(column + "   ");
		
		System.out.println("\n");
	}
}
