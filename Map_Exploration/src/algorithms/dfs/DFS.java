package algorithms.dfs;

import java.util.List;

import agents.Explorer;
import repast.simphony.query.space.grid.GridCell;

public class DFS {

	private Explorer agent;
	
	public DFS(Explorer agent) {
		this.agent = agent;
	}
	
	public GridCell<Object> execute(List<GridCell<Object>> neighborhoodCells) {
		// SimUtilities.shuffle(gridCells, RandomHelper.getUniform());
		GridCell<Object> destinationCell = null;
		for (GridCell<Object> gridCell : neighborhoodCells) {
			int row = agent.getGrid().getDimensions().getHeight() - 1 - gridCell.getPoint().getY();
			int column = gridCell.getPoint().getX();
			
			// If the point is inside the grid...
			if(row >= 0 && row < agent.getGrid().getDimensions().getHeight() && column >= 0 && column < agent.getGrid().getDimensions().getWidth()) {
				if(agent.getMatrix().getValue(row, column) == 0) {
					destinationCell = gridCell;
					break;
				}
			}
		}
		return destinationCell;		
	}
}
